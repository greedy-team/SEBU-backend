#!/usr/bin/env bash
# Isolated CI/local smoke test. Never connects to the deployed MySQL database.
set -euo pipefail
image=${1:?Usage: bash ops/deploy/smoke-prod.sh IMAGE}
umask 077
scratch=$(mktemp -d)
network="sebu-ci-${RANDOM}-$$"
mysql_id=''
app_id=''
cleanup() {
  [[ -z "$app_id" ]] || docker rm --force "$app_id" >/dev/null 2>&1 || true
  # -v removes only this disposable container's anonymous MySQL volume.
  [[ -z "$mysql_id" ]] || docker rm --force --volumes "$mysql_id" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -f -- "$scratch/mysql.env" "$scratch/app.env" "$scratch/response.json"
  rmdir -- "$scratch"
}
trap cleanup EXIT
db_password=$(openssl rand -hex 24)
printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_DATABASE=sebu\n' "$db_password" > "$scratch/mysql.env"
printf 'SPRING_PROFILES_ACTIVE=prod\nDB_URL=jdbc:mysql://sebu-ci-mysql:3306/sebu?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Seoul\nDB_USERNAME=root\nDB_PASSWORD=%s\nJWT_SECRET_BASE64=%s\nJAVA_TOOL_OPTIONS=-Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m\n' \
  "$db_password" "$(openssl rand -base64 32)" > "$scratch/app.env"
unset db_password
docker network create "$network" >/dev/null
mysql_id=$(docker run --detach --network "$network" --network-alias sebu-ci-mysql \
  --env-file "$scratch/mysql.env" mysql:8.0)
ready=false
for ((attempt=1; attempt<=90; attempt++)); do
  if docker exec "$mysql_id" sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h127.0.0.1 -e "SELECT 1"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
[[ "$ready" == true ]] || { echo 'Disposable MySQL did not become ready'; exit 1; }
app_id=$(docker run --detach --network "$network" --publish 127.0.0.1::8080 \
  --memory 768m --env-file "$scratch/app.env" "$image")
address=$(docker port "$app_id" 8080/tcp)
for ((attempt=1; attempt<=120; attempt++)); do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$app_id")" != true ]]; then
    echo 'Production smoke container exited. Inspect this isolated CI run.'
    docker logs --tail 100 "$app_id"
    exit 1
  fi
  status=$(curl --silent --max-time 5 --output "$scratch/response.json" --write-out '%{http_code}' \
    -H 'X-Forwarded-Proto: https' "http://$address/api/v1/laboratories" || true)
  if [[ "$status" == 200 ]] && python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("success") is True else 1)' "$scratch/response.json"; then
    failed=$(docker exec "$mysql_id" sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Nse "SELECT COUNT(*) FROM sebu.flyway_schema_history WHERE success = 0"')
    [[ "$failed" == 0 ]] || { echo 'Flyway history contains failed migrations'; exit 1; }
    echo 'PASS: MySQL 8.0 + prod Flyway + Hibernate validate + HTTP 200/success=true'
    exit 0
  fi
  sleep 2
done
docker logs --tail 100 "$app_id"
echo 'Production API smoke test timed out'
exit 1
