# 예체능대학 교수·연구실 수집 검수

2026-10-06 기준, 제공된 6개 URL에서 교수 18명을 수집했다. 기존 크롤러의 수집 결과를 공식 HTML 및 공개 API와 독립 대조해 이름·직위·이메일·연구소개·홈페이지 90개 값의 일치를 확인한 뒤 원문 이상값 2건을 보정했다.

| 학과 | 페이지 유형 | 교수 수 |
| --- | --- | ---: |
| 회화과 | 학과 교수소개 HTML | 1 |
| 패션디자인학과 | 학교 학과소개 + 공개 교수 API | 3 |
| 음악과 | 학과 교수소개 HTML | 5 |
| 체육학과 | 학교 학과소개 + 공개 교수 API | 2 |
| 무용과 | 학교 학과소개 + 공개 교수 API | 1 |
| 영화예술학과 | 학교 학과소개 + 공개 교수 API | 6 |

## CSV와 수집 기준

- `data/arts-sports-crawl-sources.csv`: 기존 입력 계약인 `college_name,department_name,url,parser_type`을 유지한 6개 출처. 모두 `SEJONG_STANDARD`이며 기존 fetcher가 학교 대표 홈페이지의 API 호출을 처리한다.
- `data/arts-sports-professors-reviewed.csv`: 교수별 출처, 페이지 유형, 수집값, 연구실명 유형, 검수 상태·일자·보정 사유. UTF-8이며 쉼표가 있는 필드는 CSV 인용 규칙을 따른다. 빈 칸은 미공개 값(NULL)이다.
- 공식 연구실 고유 명칭은 공개되지 않았다. 기존 승격 규칙에 따라 `교수명 교수님 연구실`을 생성하고 `name_source=GENERATED`, 모집 상태는 `UNKNOWN`으로 저장한다. 페이지의 연구실 위치를 연구실 이름으로 사용하지 않는다.
- 초빙·특임을 포함해 지정 URL에 나온 18명을 유지한다. 보조 학과 홈페이지에는 29명이 있으나 체육 4명·영화 7명은 지정 URL의 API 목록에 없다. 이는 공급 목록 차이이며 크롤러가 제외한 결과가 아니다. 보조 목록의 인원을 임의 추가하지 않았다.

## 보정 및 빈값

| 교수 | 원문 | 검수 결과 |
| --- | --- | --- |
| 최두영 | 이메일 `filmdoo@sejong.ac.kr ,` | 후행 공백·쉼표 제거 → `filmdoo@sejong.ac.kr` |
| 김형남 | 홈페이지 `https://www.sejong.ac.kr/kor/college/dance.do#none` | 현재 학과 화면을 가리키는 임시 링크 → NULL |

윤경희의 연구소개 끝 쉼표는 원문 그대로 보존했다. 김기훈·이은경·안연석의 연구소개는 원문이 비어 있어 NULL로 유지했다. 이메일은 18개 모두 서로 다르다. 홈페이지 5개는 각각 GET 200과 교수 이름을 확인했으며, 김나영의 기존 주소는 새 공식 홈페이지로 정상 이동한다.

## 배포와 검증

`V48__import_reviewed_arts_sports_professors.sql`은 기존 V42 학과 ID를 찾아 출처와 누락된 교수·연구실·소속 연결을 추가한다. 기존 프로필, 수동 홈페이지, 공식 연구실명, 모집 상태, 연구분야 연결은 덮어쓰지 않는다. 신원·출처·연구실 충돌은 본 테이블 기록 전에 차단한다. 연구분야 검색 카테고리는 이번 수집 범위에 포함하지 않는다.

격리된 H2 DB에서 기존 서비스로 6개 URL을 다시 수집해 18개 후보를 원문 스냅샷과 대조한 뒤 승인·승격했다. 교수·연구실 각 18개가 생성됐고 재승격 대상은 0개였으며, 기존 교수·연구실 각 303개는 그대로 유지됐다. 승격값과 검수 CSV 144개 필드도 모두 일치했다.

계약 테스트는 빈 DB 전체 마이그레이션, V47 업그레이드, 검수 CSV 18건과 저장값의 일치, 기존 데이터 보존, 충돌 거절 및 SQL 재실행을 검증한다.

```powershell
.\gradlew.bat test --tests '*ArtsSportsCatalogMigrationTest' --tests '*ArtsSportsCatalogMySqlMigrationTest'
```

MySQL 테스트는 Docker의 MySQL 8.0.45 또는 전용 로컬 테스트 DB를 사용한다. 후자를 사용할 때 `SEBU_ARTS_SPORTS_TEST_MYSQL_URL`은 `jdbc:mysql://127.0.0.1:13377/sebu_arts_sports_test`로 제한되며, 별도 `SEBU_ARTS_SPORTS_TEST_MYSQL_USERNAME`·`SEBU_ARTS_SPORTS_TEST_MYSQL_PASSWORD`를 지정한다. 해당 테스트는 전용 DB를 초기화한다.

기존 수집·검수·승격 절차는 [교수 크롤링 안내](professor-crawling.md)와 [교수 승격 안내](professor-promotion.md)를 따른다. 운영 DB에 직접 실행하지 않았으며, 배포 시 Flyway가 V48을 적용한다. MySQL DDL은 트랜잭션으로 되돌릴 수 없으므로 충돌로 중단되면 부분 적용 여부를 확인한 뒤 재시도한다.
