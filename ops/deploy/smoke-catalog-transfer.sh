#!/usr/bin/env bash
# Test only: clone an already migrated disposable MySQL database. Never use a deployed container.
set -euo pipefail
mysql_id=${1:?Usage: smoke-catalog-transfer.sh DISPOSABLE_MYSQL BASE_DATABASE [APP_CONTAINER]}
base_database=${2:?Missing migrated disposable database}
original_app=${3:-}
[[ "$base_database" =~ ^[a-zA-Z0-9_]+$ ]] || { echo 'Invalid database name'; exit 1; }
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
umask 077
scratch=$(mktemp -d)
source_db="sebu_catalog_verify_source_${RANDOM}_$$"
target_db="sebu_catalog_verify_target_${RANDOM}_$$"
restored_app=''
sql() {
  local database=$1
  docker exec --interactive "$mysql_id" sh -ec \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --raw --skip-column-names --default-character-set=utf8mb4 "$1"' sh "$database"
}
cleanup() {
  [[ -z "$restored_app" ]] || docker rm --force "$restored_app" >/dev/null 2>&1 || true
  # Both names are generated locally above; no supplied database is ever dropped.
  printf 'DROP DATABASE IF EXISTS `%s`; DROP DATABASE IF EXISTS `%s`;\n' "$source_db" "$target_db" \
    | sql mysql >/dev/null 2>&1 || true
  rm -f -- "$scratch/base.sql" "$scratch/source.json.gz" "$scratch/baseline.json.gz" \
    "$scratch/source-after.json.gz" "$scratch/invalid.json.gz" "$scratch/import-error.log" \
    "$scratch/app.env" "$scratch/api.json" "$scratch/health.json" "$scratch/api-connection.json"
  rmdir -- "$scratch"
}
trap cleanup EXIT

# Full schema clone retains generated columns, CHECK constraints, foreign keys and Flyway history.
docker exec "$mysql_id" sh -ec \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --skip-add-locks --default-character-set=utf8mb4 "$1"' \
  sh "$base_database" > "$scratch/base.sql"
printf 'CREATE DATABASE `%s` CHARACTER SET utf8mb4; CREATE DATABASE `%s` CHARACTER SET utf8mb4;\n' \
  "$source_db" "$target_db" | sql mysql >/dev/null
sql "$source_db" < "$scratch/base.sql"
sql "$target_db" < "$scratch/base.sql"

# These fixtures exist only in the throwaway source clone, never in the application seed DB.
sql "$source_db" <<'SQL'
INSERT INTO professor (id,department_id,name,email,position)
SELECT 9000000+c.id, MIN(d.id), CONCAT('이전검증 ',c.name),CONCAT('transfer-',c.id,'@example.invalid'),'교수'
FROM college c JOIN department d ON d.college_id=c.id
WHERE c.name IN ('자연과학대학','생명과학대학','인공지능융합대학') GROUP BY c.id,c.name;
INSERT INTO laboratory (id,professor_id,department_id,name,website_url,website_url_source,
                        description,recruitment_status,name_source)
SELECT id,id,department_id,CONCAT(name,CHAR(39),' 연구실'),
       CONCAT('https://example.invalid/',id,'?q=연구&x=',CHAR(39),'quoted',CHAR(39),'&a=1'),
       'MANUAL',CONCAT('한글 🧪',CHAR(9),'탭',CHAR(10),'줄바꿈 ',CHAR(92),' SQL; --'),
       'UNKNOWN','OFFICIAL'
FROM professor WHERE id >= 9000000;
INSERT INTO professor_department (professor_id,department_id,position)
SELECT id,department_id,position FROM professor WHERE id >= 9000000;
INSERT INTO laboratory_department (laboratory_id,department_id)
SELECT id,department_id FROM laboratory WHERE id >= 9000000;
INSERT INTO research_field (id,name) VALUES (9000001,'이전검증: 한글·로봇 🧪');
INSERT INTO laboratory_research_field (laboratory_id,research_field_id)
SELECT id,9000001 FROM laboratory WHERE id >= 9000000;
INSERT INTO research_field_category_mapping (research_field_id,category_id)
SELECT 9000001,id FROM research_field_category WHERE code='ROBOT_AUTONOMOUS_ROBOTICS';
INSERT INTO app_user (id,email) VALUES (9000001,'temporary-review-author@example.invalid');
INSERT INTO laboratory_review (id,laboratory_id,author_id,category,research_intensity,
                               compensation,atmosphere,content,participation_year,participation_term)
SELECT 9000001,MIN(id),9000001,'OTHER','MEDIUM','NONE','NORMAL','임시 후기: 절대 이전하지 않음',2026,'FIRST_SEMESTER'
FROM laboratory WHERE id >= 9000000;
INSERT INTO laboratory_review_tag (review_id,tag) VALUES (9000001,'RESEARCH_IMMERSION');
SQL

transfer=(python3 "$script_dir/catalog_transfer.py")
"${transfer[@]}" export --container "$mysql_id" --database "$source_db" --output "$scratch/source.json.gz"
"${transfer[@]}" export --container "$mysql_id" --database "$target_db" --output "$scratch/baseline.json.gz"
history_before=$(printf 'SELECT * FROM flyway_schema_history ORDER BY installed_rank;\n' | sql "$target_db" | sha256sum)

# A checksummed bundle with a DB-constraint violation fails inside the import transaction.
python3 - "$script_dir/catalog_transfer.py" "$scratch/source.json.gz" "$scratch/invalid.json.gz" <<'PY'
import gzip, importlib.util, json, sys
spec = importlib.util.spec_from_file_location('catalog_transfer', sys.argv[1])
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
with gzip.open(sys.argv[2], 'rt', encoding='utf-8') as stream:
    bundle = json.load(stream)
table = bundle['tables']['professor']
rows = [json.loads(row) for row in table['rows']]
email_index = table['columns'].index('email')
with_email = [row for row in rows if row[email_index]]
assert len(with_email) > 1
with_email[-1][email_index] = with_email[0][email_index]
raw = [json.dumps(row, ensure_ascii=False, separators=(', ', ': ')) for row in rows]
bundle['tables']['professor'] = module.seal_table(table['columns'], raw)
with gzip.open(sys.argv[3], 'wt', encoding='utf-8') as stream:
    json.dump(bundle, stream, ensure_ascii=False)
PY
if "${transfer[@]}" restore --container "$mysql_id" --database "$target_db" \
    --input "$scratch/invalid.json.gz" --confirm-target "$target_db" > "$scratch/import-error.log" 2>&1; then
  echo 'FAIL: duplicate professor email must abort catalogue import'; exit 1
fi
grep -Fq 'MySQL error 1062' "$scratch/import-error.log" || {
  echo 'FAIL: rollback fixture must reach the MySQL unique constraint inside the import transaction'; exit 1;
}
# Verify values, links and counts were rolled back, including rows deleted before the failure.
"${transfer[@]}" verify --container "$mysql_id" --database "$target_db" --input "$scratch/baseline.json.gz"

"${transfer[@]}" restore --container "$mysql_id" --database "$target_db" \
  --input "$scratch/source.json.gz" --confirm-target "$target_db"
"${transfer[@]}" verify --container "$mysql_id" --database "$target_db" --input "$scratch/source.json.gz"
history_after=$(printf 'SELECT * FROM flyway_schema_history ORDER BY installed_rank;\n' | sql "$target_db" | sha256sum)
[[ "$history_before" == "$history_after" ]] || { echo 'FAIL: target Flyway history changed'; exit 1; }
[[ "$(printf 'SELECT COUNT(*) FROM laboratory WHERE id>=9000000;\n' | sql "$target_db")" == 3 ]] || {
  echo 'FAIL: missing source-only college laboratories'; exit 1;
}
[[ "$(printf 'SELECT COUNT(*) FROM app_user UNION ALL SELECT COUNT(*) FROM laboratory_review UNION ALL SELECT COUNT(*) FROM laboratory_review_tag;\n' | sql "$target_db")" == $'0\n0\n0' ]] || {
  echo 'FAIL: temporary users/reviews leaked into restored catalogue'; exit 1;
}
# The source is read-only to the exporter; even excluded temporary reviews remain intact there.
"${transfer[@]}" export --container "$mysql_id" --database "$source_db" --output "$scratch/source-after.json.gz"
python3 - "$scratch/source.json.gz" "$scratch/source-after.json.gz" <<'PY'
import gzip, json, sys
def tables(path):
    with gzip.open(path, 'rt', encoding='utf-8') as stream:
        return json.load(stream)['tables']
assert tables(sys.argv[1]) == tables(sys.argv[2]), 'Source catalogue was modified'
PY
[[ "$(printf 'SELECT COUNT(*) FROM laboratory_review WHERE id=9000001;\n' | sql "$source_db")" == 1 ]] || {
  echo 'FAIL: source review was changed'; exit 1;
}

# Once a target has user activity, the importer must refuse to replace its catalogue.
printf "INSERT INTO app_user (id,email) VALUES (9000001,'active-target@example.invalid');\n" | sql "$target_db"
if "${transfer[@]}" restore --container "$mysql_id" --database "$target_db" \
    --input "$scratch/source.json.gz" --confirm-target "$target_db" > "$scratch/import-error.log" 2>&1; then
  echo 'FAIL: importer accepted a target with user data'; exit 1
fi
grep -Fq 'Destination contains user activity' "$scratch/import-error.log" || {
  echo 'FAIL: active target must be refused by its activity guard'; exit 1;
}
[[ "$(printf 'SELECT COUNT(*) FROM app_user WHERE id=9000001;\n' | sql "$target_db")" == 1 ]] || {
  echo 'FAIL: refused import altered target activity'; exit 1;
}
printf 'DELETE FROM app_user WHERE id=9000001;\n' | sql "$target_db"
"${transfer[@]}" verify --container "$mysql_id" --database "$target_db" --input "$scratch/source.json.gz"

if [[ -n "$original_app" ]]; then
  # Read the disposable app's environment without printing its password or token.
  python3 - "$original_app" "$target_db" "$scratch/app.env" "$scratch/api-connection.json" <<'PY'
import json, pathlib, re, subprocess, sys
app = json.loads(subprocess.check_output(['docker','inspect',sys.argv[1]], text=True))[0]
environment = dict(item.split('=', 1) for item in app['Config']['Env'])
environment['DB_URL'], count = re.subn(r'(?<=:3306/)[A-Za-z0-9_]+', sys.argv[2], environment['DB_URL'])
assert count == 1, 'Unexpected disposable JDBC URL'
allowed = {'DB_URL','DB_USERNAME','DB_PASSWORD','SPRING_PROFILES_ACTIVE','JWT_SECRET_BASE64',
           'MONITORING_TOKEN','APP_AUTH_CSRF_ALLOWEDORIGINS','JAVA_TOOL_OPTIONS'}
pathlib.Path(sys.argv[3]).write_text(''.join(f'{key}={value}\n' for key,value in environment.items() if key in allowed), encoding='utf-8')
networks = list(app['NetworkSettings']['Networks'])
assert len(networks) == 1
pathlib.Path(sys.argv[4]).write_text(json.dumps({'image': app['Image'], 'network': networks[0]}), encoding='utf-8')
PY
  image=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["image"])' "$scratch/api-connection.json")
  network=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["network"])' "$scratch/api-connection.json")
  restored_app=$(docker run --detach --network "$network" --publish 127.0.0.1::8080 \
    --memory 768m --env-file "$scratch/app.env" "$image")
  address=$(docker port "$restored_app" 8080/tcp)
  ready=false
  for ((attempt=1; attempt<=120; attempt++)); do
    [[ "$(docker inspect --format '{{.State.Running}}' "$restored_app")" == true ]] || break
    if curl --silent --fail --max-time 5 -H 'X-Forwarded-Proto: https' \
        "http://$address/actuator/health/readiness" > "$scratch/health.json" \
      && curl --silent --fail --max-time 5 -H 'X-Forwarded-Proto: https' \
        "http://$address/api/v1/laboratories" > "$scratch/api.json"; then
      if python3 - "$scratch/health.json" "$scratch/api.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as stream:
    assert json.load(stream) == {'status':'UP'}
with open(sys.argv[2], encoding='utf-8') as stream:
    response = json.load(stream)
assert response['success'] is True
fixtures = [lab for lab in response['data']['laboratories'] if lab['id'] >= 9000000]
assert len(fixtures) == 3
assert {lab['college']['name'] for lab in fixtures} == {'자연과학대학','생명과학대학','인공지능융합대학'}
for lab in fixtures:
    assert lab['reviewCount'] == 0 and lab['bookmarkCount'] == 0
    assert lab['websiteUrl'].startswith('https://example.invalid/')
    assert '이전검증: 한글·로봇 🧪' in lab['researchFields']
    assert any(category['code'] == 'ROBOT_AUTONOMOUS_ROBOTICS' for category in lab['researchFieldCategories'])
PY
      then ready=true; break; fi
    fi
    sleep 2
  done
  [[ "$ready" == true ]] || { echo 'FAIL: restored catalogue failed Hibernate/readiness/API validation'; exit 1; }
  echo 'PASS: restored catalogue passes Hibernate validation and API exposes all three missing-college fixtures with zero reviews'
fi
echo 'PASS: complete catalogue restore preserves IDs/text/links and Flyway history; excludes temporary reviews; source unchanged; failed import rolls back; active target refused'
