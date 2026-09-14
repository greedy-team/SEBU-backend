# 교수 정보 1차 크롤링 실행 안내

## 동작 범위

교수진 목록 페이지에서 다음 값을 수집하여 `professor_crawl_candidate`에 저장합니다.

- 교수 이름
- 직위
- 이메일
- 연구 분야
- 교수 홈페이지

교수진 목록에서 확인할 수 없는 연구실 이름은 후보 테이블에 `NULL`로 저장합니다. 검수 후 본 테이블로 승격할 때 `교수 이름 + " 교수님 연구실"`을 생성하고 `GENERATED`로 구분합니다. 석사·박사 인원은 후속 기능에서 수기로 관리합니다.

크롤러는 일반 서비스 실행 시 동작하지 않습니다. `crawler` 프로필과 `app.professor-crawler.enabled=true`를 함께 지정한 일회성 실행에서만 동작하고, 작업이 끝나면 애플리케이션이 종료됩니다.

## 사전 확인

Java 21, MySQL, `curl` 명령이 필요합니다.

```powershell
java -version
curl.exe --version
```

프로젝트 폴더로 이동한 뒤 MySQL 접속 정보를 현재 PowerShell 세션에만 설정합니다. 실제 비밀번호는 화면에 표시하거나 명령 기록, 설정 파일, Git에 남기지 않습니다.

```powershell
Set-Location "<프로젝트_루트>"
$env:DB_URL = "jdbc:mysql://localhost:3306/sebu?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"
$env:DB_USERNAME = "root"
$sebuDbPassword = Read-Host "MySQL 비밀번호" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $sebuDbPassword).Password
```

`prod,crawler` 프로필로 처음 실행하면 Flyway가 적용되지 않은 마이그레이션을 먼저 적용합니다. 이미 V8과 V9가 적용된 DB라면 V10부터 V12까지 이어서 적용됩니다.

## URL 1개만 먼저 실행

MySQL에서 컴퓨터공학과 출처 ID를 확인합니다.

```sql
SELECT id, source_name, source_url
FROM crawl_source
WHERE source_url = 'https://dept.sejong.ac.kr/cedpt/intro/professor.do';
```

프로젝트 루트에서 조회된 ID를 넣어 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=prod,crawler --app.professor-crawler.enabled=true --app.professor-crawler.source-id=<조회된_ID>"
```

`<조회된_ID>`는 꺾쇠까지 포함해 실제 숫자로 바꿉니다. 예를 들어 ID가 1이면 `--app.professor-crawler.source-id=1`로 입력합니다. 정상적으로 끝나면 애플리케이션도 자동으로 종료됩니다.

실행 결과를 확인합니다.

```sql
SELECT id, last_crawl_status, last_crawled_at, last_error_message
FROM crawl_source
WHERE id = <조회된_ID>;

SELECT version, description, success
FROM flyway_schema_history
WHERE version IN ('8', '9', '10', '11', '12')
ORDER BY installed_rank;

SELECT COUNT(*) AS total_candidate_count,
       SUM(review_status = 'PENDING') AS pending_count,
       SUM(is_stale = FALSE) AS current_candidate_count
FROM professor_crawl_candidate
WHERE source_id = <조회된_ID>;

SELECT professor_name,
       source_identity_key,
       position,
       email,
       research_introduction,
       homepage_url,
       laboratory_name,
       source_url_at_crawl,
       parser_type_at_crawl,
       review_status
FROM professor_crawl_candidate
WHERE source_id = <조회된_ID>
  AND is_stale = FALSE
ORDER BY professor_name;
```

정상 실행이면 `last_crawl_status`는 `SUCCESS`이고 새 후보의 `review_status`는 `PENDING`입니다. 재실행할 때 값이 달라진 후보도 다시 `PENDING`으로 돌아가므로 변경 내용을 재검수할 수 있습니다.

## 활성 URL 12개 전체 실행

URL 1개의 결과를 확인한 뒤 `source-id` 옵션을 빼면 활성화된 출처를 모두 순서대로 실행합니다. 기본적으로 요청 사이에 1초를 기다립니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=prod,crawler --app.professor-crawler.enabled=true"
```

출처별 처리 상태는 다음 쿼리로 확인합니다.

```sql
SELECT id,
       source_name,
       source_url,
       last_crawl_status,
       last_crawled_at,
       last_error_message
FROM crawl_source
ORDER BY id;
```

한 출처에서 실패해도 나머지 출처는 계속 처리합니다. 최종적으로 실패가 하나라도 있으면 프로세스는 실패로 종료되고, 각 실패 원인은 `crawl_source.last_error_message`와 애플리케이션 로그에 남습니다.

크롤링 작업을 모두 마치면 현재 PowerShell 세션에서 비밀번호를 제거합니다.

```powershell
Remove-Item Env:DB_PASSWORD
Remove-Variable sebuDbPassword
```

## 출처 URL을 직접 변경할 때

URL이나 파서 종류를 MySQL에서 직접 바꿀 때는 이전 실행 상태와 낙관적 잠금 버전도 함께 갱신해야 합니다. 아래 형태로 한 행을 원자적으로 수정합니다.

```sql
UPDATE crawl_source
SET source_url = 'https://dept.sejong.ac.kr/example/intro/professor.do',
    parser_type = 'SEJONG_STANDARD',
    last_crawled_at = NULL,
    last_crawl_status = 'NOT_STARTED',
    last_error_message = NULL,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE id = <변경할_ID>;
```

화면 표시용 `source_name`만 바꿀 때도 `updated_at`과 `version = version + 1`을 함께 수정합니다. 크롤러가 실행 중일 때는 출처 행을 직접 수정하지 않습니다.

## 검수 전 주의사항

- 후보 데이터는 바로 서비스 본 테이블에 반영되지 않습니다.
- 연구실 이름이 `NULL`인 후보도 다른 정보가 올바르면 승인할 수 있습니다. 본 테이블 승격 방법은 `docs/professor-promotion.md`를 따릅니다.
- 페이지에서 사라진 교수는 삭제하지 않고 `is_stale = TRUE`로 표시하여 이력을 보존합니다.
- 홈페이지가 Google Scholar나 외부 연구자 페이지인 경우도 있으므로 사람이 최종 검수합니다.
- 이름이 같아도 이메일 또는 홈페이지가 다르면 서로 다른 후보로 저장합니다.
- 크롤링 중 URL이나 파서 설정이 바뀌면 해당 실행 결과는 저장하지 않습니다.
- 출처 URL과 사용한 파서 종류는 후보 행에 스냅샷으로 남겨 원본을 추적할 수 있습니다.
