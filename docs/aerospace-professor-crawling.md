# 우주항공 교수 목록 크롤링

## 범위와 기존 크롤러와의 관계

기존 `ProfessorCrawlerRunner → ProfessorCrawlCoordinator → ProfessorCrawlPersistenceService`를 그대로 사용한다.
출처의 `parser_type`으로 HTML 추출 방식만 선택한다. 크롤링/검수/승격 파이프라인을 복제하지 않는다.

| 파서 종류 | 대상 |
| --- | --- |
| `SEJONG_STANDARD` | 기존 세종대 일반 교수 목록 |
| `SEJONG_QUANTUM` | 기존 양자지능정보학과 교수 목록 |
| `SEJONG_AEROSPACE` | 우주항공시스템공학부 세 전공 교수 목록 |

지원 URL:

- 우주항공공학전공: https://ae.sejong.ac.kr/shop_contents/myboard_list.htm?myboard_code=professor&category_idx=82031
- 지능형드론융합전공: https://ae.sejong.ac.kr/shop_contents/myboard_list.htm?myboard_code=professor&category_idx=82041
- 항공시스템공학전공: https://ae.sejong.ac.kr/shop_contents/myboard_list.htm?myboard_code=professor&category_idx=82051

세 URL은 각각의 전공에 연결되는 `crawl_source`로 등록한다. 후보의 소속은 이 출처를 기준으로 한다.
이 PR은 코드 지원만 추가한다. 전공/단과대 또는 `crawl_source` 운영 데이터는 자동 생성하지 않으며,
실제 후보 저장·자동 승인·본 테이블 승격·연구 분야 추출·카테고리 매핑을 실행하지 않는다.

## 추출 필드

| 페이지 항목 | 후보 컬럼 | 처리 |
| --- | --- | --- |
| 성함 (`.name`) | `professor_name` | 별도 `i` 태그의 `교수` 표시 제외, 영문 이름 내부 공백 유지 |
| 직 위 | `position` | 전임/연구중점전임/비전임 원문 유지, 자동 제외 없음 |
| 이메일 | `email` | 이메일 값만 추출, 기존 도메인 정규화 사용 |
| 전 공 | `research_introduction` | 전공 원문 저장, 쉼표·슬래시·숫자(예: 3D)는 유지 |
| 목록에 없음 | `laboratory_name` | `NULL` |
| 목록에 없음 | `homepage_url` | `NULL` |

- `교수실`, `연구실`의 건물·호실, 연락처, 학사/석사/박사는 연구 분야로 추출하지 않는다.
- 카드 전체를 감싼 `myboard_read.htm` 링크는 교수 상세보기이므로 연구실 홈페이지로 저장하지 않는다.
- 빈 값이나 `-`는 `NULL`로 유지한다. 학력/학과/이름으로 연구 분야를 추측하지 않는다.
- 별도 연구실 소개 페이지의 공식 연구실명/홈페이지/상세 소개를 연결하는 작업은 이번 범위에 포함하지 않는다.
- 후보의 연구실명이 없는 경우 기존 승격 정책인 `교수 이름 + " 교수님 연구실"`, `GENERATED`를 유지한다.

## 실패 및 재실행

페이지의 `.sub04_wrap > ul > li` 카드와 `총 N건, 1/1 페이지` 요약을 확인한다.
카드가 없거나 이름이 빠졌거나 총 건수와 카드 수가 다르면 전체 결과를 실패 처리한다.
현재 세 전공의 단일 페이지를 지원하며, 향후 여러 페이지가 되면 자동으로 첫 페이지만 저장하지 않고
`SEJONG_AEROSPACE_MULTIPLE_PAGES_NOT_SUPPORTED`로 중단한다. 그때 별도 페이지 순회 기능을 추가한다.

파싱 실패는 기존 Coordinator의 실패 처리 경로로 전달되어 출처가 `FAILED`로 기록된다.
후보 저장 함수는 호출되지 않으므로 일부만 읽은 결과로 기존 후보를 `is_stale=TRUE` 처리하지 않는다.
정상 재실행은 기존 후보 조정 로직을 사용한다. 변경 없는 후보의 승인 상태는 보존하고,
수집 값이 바뀐 후보는 기존 정책에 따라 다시 검수한다.
같은 교수가 여러 전공에 나오는 경우 출처별 후보를 보존하며 본 테이블 통합은 기존 승격 서비스 책임이다.

## DB 배포와 출처 등록

`V42__allow_aerospace_professor_crawl_parser.sql`은 다음 두 CHECK 제약의 허용값만 확장한다.

- `crawl_source.parser_type`
- `professor_crawl_candidate.parser_type_at_crawl`

기존 행/승인/승격 이력은 변경하지 않는다. 적용된 V8/V10 등의 파일도 수정하지 않는다.
DDL은 두 테이블에 메타데이터 잠금/검증을 일으킬 수 있으므로 크롤링 작업을 중지하고 배포한다.
MySQL DDL은 전체 파일 단위로 롤백되지 않으므로 사전 백업과 정상 적용 확인이 필요하다.
신규 종류를 저장한 뒤에는 이 enum을 모르는 구버전 크롤러로 되돌리지 않는다.

### 병합 순서

작성 시점 `develop` 최신은 V39이고 열린 PR #72/#73이 V40/V41을 사용 중이다.
버전 충돌을 피하기 위해 V42를 사용했다. **V40/V41을 포함하는 PR들을 먼저 병합·적용하고 이 PR을 병합한다.**
병합 직전 최신 `develop`을 반영하고 마이그레이션/테스트를 다시 확인한다.
V42를 먼저 적용한 DB에 V40/V41을 뒤늦게 넣으면 Flyway 순서 문제가 생길 수 있다.
`outOfOrder` 활성화나 `repair`로 우회하지 않고 적용 전에 순서를 조정한다.

출처 등록은 대상 DB의 실제 `department.id`와 기존 URL 등록 여부를 확인한 후 별도 작업으로 진행한다.
등록 시 `parser_type='SEJONG_AEROSPACE'`를 사용하며 기존 출처에 일반 파서를 그대로 지정하면 안 된다.

```sql
SELECT c.name AS college_name, d.id AS department_id, d.name AS department_name,
       s.id AS source_id, s.source_url, s.parser_type
FROM department d
JOIN college c ON c.id = d.college_id
LEFT JOIN crawl_source s ON s.department_id = d.id
WHERE d.name LIKE '%우주항공%'
   OR d.name LIKE '%지능형드론%'
   OR d.name LIKE '%항공시스템%'
ORDER BY c.name, d.name, s.id;
```

등록 이후의 일회성 실행 및 보안상 주의사항은 [기존 실행 안내](professor-crawling.md)를 따른다.
항상 실제로 확인한 `source-id` 한 개부터 실행한다.

## 테스트

2026-09-12에 실제 HTTP 수집기와 새 파서로 DB 연결 없이 확인한 결과:
우주항공공학전공 15건, 지능형드론융합전공 13건, 항공시스템공학전공 9건을 추출했다.
합계 37건은 전공별 후보 건수이며 겸임이 포함된다. 전공 원문이 빈 1건은 `NULL`을 유지했다.
이 수치는 확인 시점의 결과이며 테스트에 고정된 운영 교수 수는 아니다.

```powershell
.\gradlew.bat test --tests "com.sebu.backend.crawling.*" --no-daemon
```

고정 HTML fixture는 실제 목록 구조와 가상 교수 데이터를 사용하며 테스트 중 외부 사이트를 호출하지 않는다.
파서 선택, 누락 정보, 영문 이름, 다중 전공 동일 인물의 식별키, 건수 불일치, 다중 페이지 실패를 검증한다.
Spring/H2 통합 테스트는 신규 `PENDING` 저장, 파서 출처 기록, 재실행 중복 방지 및 실패 시 기존 후보 보존을 검증한다.

`AerospaceCrawlParserMySqlMigrationTest`는 기본적으로 Docker의 MySQL 8.0.45를 사용한다.
빈 DB 전체 마이그레이션, V39 데이터 보존 업그레이드, 세 enum 저장, 잘못된 enum 차단 및 Hibernate 검증을 수행한다.
Docker를 쓰지 않는 로컬 환경에서는 다음 **별도 폐기 가능한 DB**만 명시적으로 허용한다.

```powershell
$env:SEBU_AEROSPACE_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:13342/sebu_aerospace_migration_test'
# 별도 테스트 계정은 SEBU_AEROSPACE_TEST_MYSQL_USERNAME / PASSWORD 환경변수로 전달한다.
# 이 테스트는 해당 전용 DB를 clean한다. 서비스 DB의 URL/비밀번호를 절대 지정하지 않는다.
.\gradlew.bat test --tests "*AerospaceCrawlParserMySqlMigrationTest" --no-daemon
Remove-Item Env:SEBU_AEROSPACE_TEST_MYSQL_URL
```
