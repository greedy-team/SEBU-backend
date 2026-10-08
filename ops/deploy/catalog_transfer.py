#!/usr/bin/env python3
"""Private, catalogue-only MySQL snapshot transfer for an unused production DB.

Never copy authentication, user activity, crawl candidates or Flyway history.
The source is read-only. Restore replaces the destination catalogue atomically;
it requires empty activity tables and a stopped application (or rehearsal DB).
"""

import argparse
import datetime
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


TABLES = (
    "college", "department", "crawl_source", "professor", "laboratory",
    "professor_department", "laboratory_department", "research_field",
    "laboratory_research_field", "research_field_category",
    "research_field_category_mapping",
)
EXCLUDED = (
    "app_user", "refresh_token", "account_recovery_token", "bookmark",
    "community_post", "community_comment", "community_post_like",
    "community_post_bookmark", "laboratory_review", "laboratory_review_tag",
    "professor_crawl_candidate", "laboratory_research_field_candidate",
)
KNOWN_TABLES = set(TABLES + EXCLUDED + ("flyway_schema_history",))
IDENTIFIER = re.compile(r"[A-Za-z_][A-Za-z0-9_]{0,63}\Z")
TEMP_DATABASE = re.compile(r"sebu_catalog_verify_[a-z0-9_]+\Z")
AUTH = '''set -eu
if [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ]; then
  MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"
else
  MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
fi
export MYSQL_PWD
exec mysql --user=root --default-character-set=utf8mb4 --batch --raw --skip-column-names "$@"
'''
SESSION = ("SET SESSION time_zone='+00:00'; SET NAMES utf8mb4; "
           "SET SESSION foreign_key_checks=1; SET SESSION information_schema_stats_expiry=0; "
           "SET SESSION sql_mode=CONCAT_WS(',',@@SESSION.sql_mode,"
           "'STRICT_ALL_TABLES','NO_AUTO_VALUE_ON_ZERO');\n")
METADATA_SQL = """
SELECT JSON_ARRAY('server', @@server_uuid, DATABASE());
SELECT JSON_ARRAY('table', TABLE_NAME, ENGINE)
FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;
SELECT JSON_ARRAY('column', TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
 COLUMN_DEFAULT, EXTRA, GENERATION_EXPRESSION, CHARACTER_SET_NAME, COLLATION_NAME)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT JSON_ARRAY('key', TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME,
 ORDINAL_POSITION, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME)
FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;
SELECT JSON_ARRAY('constraint', TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE)
FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
SELECT JSON_ARRAY('reference', TABLE_NAME, CONSTRAINT_NAME, UPDATE_RULE, DELETE_RULE)
FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
SELECT JSON_ARRAY('check', CONSTRAINT_NAME, CHECK_CLAUSE)
FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
ORDER BY CONSTRAINT_NAME;
SELECT JSON_ARRAY('migration', VERSION, DESCRIPTION, TYPE, SCRIPT,
 CAST(CHECKSUM AS CHAR), CAST(SUCCESS AS CHAR))
FROM flyway_schema_history ORDER BY INSTALLED_RANK;
"""


class TransferError(RuntimeError):
    pass


def identifier(value):
    if not isinstance(value, str) or not IDENTIFIER.fullmatch(value):
        raise TransferError("Invalid SQL identifier")
    return "`" + value + "`"


def sql_literal(value):
    if value is None:
        return "NULL"
    if not isinstance(value, str):
        raise TransferError("Snapshot cells must be strings or null")
    return "CONVERT(X'" + value.encode("utf-8").hex() + "' USING utf8mb4)"


class DockerMySQL:
    def __init__(self, container, database):
        identifier(database)
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", container):
            raise TransferError("Invalid Docker container name")
        self.container, self.database = container, database

    def run(self, sql):
        result = subprocess.run(
            ["docker", "exec", "-i", self.container, "sh", "-c", AUTH,
             "catalog-transfer", self.database],
            input=sql, text=True, encoding="utf-8", capture_output=True,
        )
        if result.returncode:
            # MySQL errors can contain professor names or raw values. Do not log them.
            code = re.search(r"ERROR (\d+)", result.stderr)
            suffix = " (MySQL error " + code.group(1) + ")" if code else ""
            raise TransferError("MySQL command failed; transaction was not committed" + suffix)
        return result.stdout

    def app_stopped(self, name):
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", name):
            raise TransferError("Invalid application container name")
        result = subprocess.run(
            ["docker", "inspect", "--format", "{{json .State}}", name],
            text=True, encoding="utf-8", capture_output=True,
        )
        if result.returncode:
            raise TransferError("Application container must exist and be stopped")
        state = json.loads(result.stdout)
        if state.get("Running") or state.get("Status") not in ("exited", "created"):
            raise TransferError("Stop the destination application before restore")


def seal_table(columns, rows):
    digest = hashlib.sha256()
    for row in rows:
        digest.update(row.encode("utf-8") + b"\n")
    return {"columns": list(columns), "rows": list(rows), "count": len(rows),
            "sha256": digest.hexdigest()}


def parse_snapshot(stdout):
    result = {"format": 1, "schema": [], "migrations": [], "excluded_counts": {},
              "tables": {name: {"rows": []} for name in TABLES}}
    for line in stdout.splitlines():
        if not line:
            continue
        item = json.loads(line)
        kind = item[0]
        if kind == "server":
            result["source"] = {"server_uuid": item[1], "database": item[2]}
        elif kind == "migration":
            result["migrations"].append(item[1:])
        elif kind == "excluded":
            result["excluded_counts"][item[1]] = int(item[2])
        elif kind == "row":
            if item[1] not in TABLES:
                raise TransferError("Unexpected snapshot table")
            result["tables"][item[1]]["rows"].append(item[2])
        elif kind in ("table", "column", "key", "constraint", "reference", "check"):
            result["schema"].append(item)
        else:
            raise TransferError("Unexpected MySQL result")
    return result


def schema_columns(schema, table):
    # GENERATION_EXPRESSION, not EXTRA: timestamp defaults also say DEFAULT_GENERATED.
    return [row[2] for row in schema if row[0] == "column" and row[1] == table and not row[7]]


def primary_key(schema, table):
    return [row[3] for row in schema
            if row[0] == "key" and row[1] == table and row[2] == "PRIMARY"]


def check_schema(schema):
    tables = [row[1] for row in schema if row[0] == "table"]
    if len(tables) != len(KNOWN_TABLES) or set(tables) != KNOWN_TABLES:
        raise TransferError("Schema has missing or unknown tables; review the allowlist")
    if any(row[2] != "InnoDB" for row in schema if row[0] == "table"):
        raise TransferError("Consistent transfer requires InnoDB tables")
    for table in TABLES:
        columns, keys = schema_columns(schema, table), primary_key(schema, table)
        if not columns or not keys or not set(keys) <= set(columns):
            raise TransferError("Catalogue columns or primary key missing")
        for column in columns:
            identifier(column)
        for row in schema:
            if row[0] == "key" and row[1] == table and row[5] is not None:
                if row[5] not in TABLES:
                    raise TransferError("Catalogue unexpectedly references excluded data")


def row_expression(columns):
    return "JSON_ARRAY(" + ",".join(
        "CAST(" + identifier(column) + " AS CHAR CHARACTER SET utf8mb4)" for column in columns
    ) + ")"


def snapshot(client):
    discovery = parse_snapshot(client.run(SESSION + METADATA_SQL))
    check_schema(discovery["schema"])
    sql = SESSION + "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;\n"
    sql += "SET TRANSACTION READ ONLY; START TRANSACTION WITH CONSISTENT SNAPSHOT;\n"
    sql += METADATA_SQL
    for table in EXCLUDED:
        sql += "SELECT JSON_ARRAY('excluded'," + sql_literal(table) + ",COUNT(*)) FROM " + identifier(table) + ";\n"
    for table in TABLES:
        columns = schema_columns(discovery["schema"], table)
        order = ",".join(identifier(key) for key in primary_key(discovery["schema"], table))
        sql += "SELECT JSON_ARRAY('row'," + sql_literal(table) + ",CAST(" + row_expression(columns)
        sql += " AS CHAR CHARACTER SET utf8mb4)) FROM " + identifier(table) + " ORDER BY " + order + ";\n"
    sql += "COMMIT;\n"
    result = parse_snapshot(client.run(sql))
    if result["schema"] != discovery["schema"] or result["migrations"] != discovery["migrations"]:
        raise TransferError("Schema changed during export; retry after migrations finish")
    for table in TABLES:
        result["tables"][table] = seal_table(
            schema_columns(result["schema"], table), result["tables"][table]["rows"])
    result["created_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    validate_bundle(result)
    return result


def category_order(table):
    columns = table["columns"]
    id_index, parent_index = columns.index("id"), columns.index("parent_id")
    pending = {json.loads(raw)[id_index]: (raw, json.loads(raw)[parent_index])
               for raw in table["rows"]}
    if len(pending) != len(table["rows"]):
        raise TransferError("Duplicate category ID")
    output, emitted = [], set()
    while pending:
        ready = [key for key, (_, parent) in pending.items() if parent is None or parent in emitted]
        if not ready:
            raise TransferError("Category parent is missing or cyclic")
        for key in ready:
            output.append(pending.pop(key)[0])
            emitted.add(key)
    return output


def validate_bundle(bundle):
    try:
        if bundle["format"] != 1 or set(bundle["tables"]) != set(TABLES):
            raise TransferError("Unsupported catalogue bundle")
        check_schema(bundle["schema"])
        identifier(bundle["source"]["database"])
        if not isinstance(bundle["source"]["server_uuid"], str) or not bundle["source"]["server_uuid"]:
            raise TransferError("Missing source identity")
        if set(bundle["excluded_counts"]) != set(EXCLUDED):
            raise TransferError("Incomplete excluded-table audit")
        if any(type(value) is not int or value < 0 for value in bundle["excluded_counts"].values()):
            raise TransferError("Invalid excluded-table counts")
        if not bundle["migrations"] or any(len(row) != 6 or row[-1] != "1" for row in bundle["migrations"]):
            raise TransferError("Source migrations are missing or failed")
        for name in TABLES:
            table = bundle["tables"][name]
            if table["columns"] != schema_columns(bundle["schema"], name):
                raise TransferError("Bundle column mismatch")
            seen_keys = set()
            key_indexes = [table["columns"].index(key) for key in primary_key(bundle["schema"], name)]
            for raw in table["rows"]:
                row = json.loads(raw)
                if not isinstance(row, list) or len(row) != len(table["columns"]):
                    raise TransferError("Invalid row width")
                if any(cell is not None and not isinstance(cell, str) for cell in row):
                    raise TransferError("Snapshot cells must be strings or null")
                key = tuple(row[index] for index in key_indexes)
                if None in key or key in seen_keys:
                    raise TransferError("Missing or duplicate primary key")
                seen_keys.add(key)
            if table != seal_table(table["columns"], table["rows"]):
                raise TransferError("Catalogue count or digest mismatch")
        category_order(bundle["tables"]["research_field_category"])
    except (KeyError, TypeError, ValueError, IndexError) as error:
        raise TransferError("Malformed catalogue bundle") from error


def matching_schema(actual, expected):
    if actual["schema"] != expected["schema"] or actual["migrations"] != expected["migrations"]:
        raise TransferError("Source and destination schema/migration identity differ")


def require_empty_activity(bundle):
    if any(bundle["excluded_counts"].values()):
        raise TransferError("Destination contains user activity or crawl candidates; restore refused")


def verify(client, bundle):
    validate_bundle(bundle)
    actual = snapshot(client)
    matching_schema(actual, bundle)
    require_empty_activity(actual)
    for table in TABLES:
        if actual["tables"][table] != bundle["tables"][table]:
            raise TransferError("Destination catalogue differs: " + table)
    return actual


def insert_rows(table, columns, rows):
    prefix = "INSERT INTO " + identifier(table) + " (" + ",".join(identifier(c) for c in columns) + ") VALUES\n"
    for start in range(0, len(rows), 50):
        values = ["(" + ",".join(sql_literal(cell) for cell in json.loads(raw)) + ")"
                  for raw in rows[start:start + 50]]
        yield prefix + ",\n".join(values) + ";\n"


def restore(client, bundle, confirm_target, app_container=None):
    validate_bundle(bundle)
    if confirm_target != client.database:
        raise TransferError("--confirm-target must exactly match the destination database")
    if app_container:
        client.app_stopped(app_container)
    elif not TEMP_DATABASE.fullmatch(client.database):
        raise TransferError("A stopped --app-container is required outside a rehearsal database")
    before = snapshot(client)
    matching_schema(before, bundle)
    require_empty_activity(before)
    if before["source"] == bundle["source"]:
        raise TransferError("Cannot restore into the source database")
    sql = SESSION + "SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; START TRANSACTION;\n"
    sql += "CREATE TEMPORARY TABLE _sebu_catalog_guard (violations BIGINT NOT NULL CHECK (violations=0));\n"
    sql += "CREATE TEMPORARY TABLE _sebu_catalog_expected (table_name VARCHAR(64), row_hash CHAR(64), PRIMARY KEY(table_name,row_hash));\n"
    # Recheck within the write transaction; it must never erase user activity.
    for table in EXCLUDED:
        sql += "INSERT INTO _sebu_catalog_guard SELECT COUNT(*) FROM " + identifier(table) + ";\n"
    for table in reversed(TABLES):
        if table == "research_field_category":
            columns = before["tables"][table]["columns"]
            for raw in reversed(category_order(before["tables"][table])):
                value = json.loads(raw)[columns.index("id")]
                sql += "DELETE FROM research_field_category WHERE id=" + sql_literal(value) + ";\n"
        else:
            sql += "DELETE FROM " + identifier(table) + ";\n"
    for table in TABLES:
        data = bundle["tables"][table]
        rows = category_order(data) if table == "research_field_category" else data["rows"]
        sql += "".join(insert_rows(table, data["columns"], rows))
        hashes = [hashlib.sha256(raw.encode("utf-8")).hexdigest() for raw in rows]
        for start in range(0, len(hashes), 100):
            sql += "INSERT INTO _sebu_catalog_expected VALUES " + ",".join(
                "(" + sql_literal(table) + "," + sql_literal(value) + ")" for value in hashes[start:start + 100]
            ) + ";\n"
        sql += "INSERT INTO _sebu_catalog_guard SELECT ABS(COUNT(*)-" + str(data["count"]) + ") FROM " + identifier(table) + ";\n"
        expression = row_expression(data["columns"])
        sql += "INSERT INTO _sebu_catalog_guard SELECT COUNT(*) FROM " + identifier(table)
        sql += " WHERE SHA2(CAST(" + expression + " AS CHAR CHARACTER SET utf8mb4),256) NOT IN "
        sql += "(SELECT row_hash FROM _sebu_catalog_expected WHERE table_name=" + sql_literal(table) + ");\n"
        for column in bundle["schema"]:
            if column[0] == "column" and column[1] == table and "auto_increment" in column[6]:
                sql += "INSERT INTO _sebu_catalog_guard SELECT COUNT(*) FROM information_schema.TABLES "
                sql += "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=" + sql_literal(table)
                sql += " AND COALESCE(AUTO_INCREMENT,0) <= COALESCE((SELECT MAX(" + identifier(column[2])
                sql += ") FROM " + identifier(table) + "),0);\n"
    sql += "COMMIT; SELECT JSON_ARRAY('committed');\n"
    output = client.run(sql)
    if not any(json.loads(line) == ["committed"] for line in output.splitlines() if line):
        raise TransferError("Commit acknowledgement missing; verify destination before retrying")
    return verify(client, bundle)


def write_bundle(path, bundle):
    validate_bundle(bundle)
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".catalog-", suffix=".json.gz", dir=path.parent)
    try:
        os.chmod(temporary, 0o600)
        with os.fdopen(descriptor, "wb") as output:
            with gzip.GzipFile(fileobj=output, mode="wb", mtime=0) as compressed:
                compressed.write(json.dumps(bundle, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def read_bundle(path):
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        bundle = json.load(stream)
    validate_bundle(bundle)
    return bundle


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("export", "restore", "verify"))
    parser.add_argument("--container", required=True)
    parser.add_argument("--database", required=True)
    parser.add_argument("--output")
    parser.add_argument("--input")
    parser.add_argument("--confirm-target")
    parser.add_argument("--app-container")
    args = parser.parse_args(argv)
    try:
        client = DockerMySQL(args.container, args.database)
        if args.command == "export":
            if not args.output or args.input:
                raise TransferError("export requires --output")
            bundle = snapshot(client)
            write_bundle(args.output, bundle)
        else:
            if not args.input or args.output:
                raise TransferError("restore/verify require --input")
            bundle = read_bundle(args.input)
            if args.command == "restore":
                restore(client, bundle, args.confirm_target, args.app_container)
            else:
                verify(client, bundle)
        print(json.dumps({"status": "ok", "command": args.command,
                          "counts": {name: bundle["tables"][name]["count"] for name in TABLES}}, ensure_ascii=False))
        return 0
    except (TransferError, OSError, ValueError) as error:
        message = str(error) if isinstance(error, TransferError) else "Unable to read/write catalogue or run Docker"
        print("Catalogue transfer refused: " + message, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
