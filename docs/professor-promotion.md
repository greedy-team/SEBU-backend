# 검수 완료 교수 후보 승격 실행 안내

## 목적과 데이터 흐름

검수가 끝난 현재 후보만 서비스 본 테이블로 옮깁니다.

```text
crawl_source
    ↓ 크롤링
professor_crawl_candidate
    ↓ APPROVED + is_stale = FALSE
promotion 일회성 실행
    ├─ professor ─ professor_department
    └─ laboratory ─ laboratory_department
```

승격 기능은 일반 서버 실행에서는 동작하지 않습니다. `promotion` 프로필과
`app.candidate-promotion.enabled=true`를 함께 지정한 경우에만 한 번 실행되고,
작업이 끝나면 애플리케이션이 종료됩니다.
`crawler`와 `promotion` 프로필은 동시에 사용할 수 없으며, 함께 지정하면 데이터 처리
전에 애플리케이션이 실패합니다.

## 저장 규칙

후보 값은 다음과 같이 본 테이블에 저장됩니다.

| 후보 | 본 테이블 |
|---|---|
| `professor_name` | `professor.name` |
| `position` | `professor.position` |
| `email` | `professor.email` |
| `research_introduction` | `laboratory.description` |
| `homepage_url` | `laboratory.website_url` |

연구실 이름이 있으면 그 값을 저장하고 `name_source`를 `OFFICIAL`로 기록합니다.
연구실 이름이 `NULL`이면 `교수 이름 + " 교수님 연구실"`을 저장하고
`name_source`를 `GENERATED`로 기록합니다. 새 연구실의 모집 상태는 `UNKNOWN`입니다.

재크롤링 후 다시 검수하여 승인한 후보는 같은 교수와 연구실을 갱신합니다.
이때 모집 상태처럼 이후 사람이 관리하는 값은 덮어쓰지 않습니다. 같은 승인본을
다시 실행해도 본 데이터가 중복 생성되지 않습니다.

홈페이지는 출처를 함께 저장합니다. 후보 승격으로 반영한 URL은
`website_url_source = CRAWLED`, 관리 서비스에서 검증하여 입력한 URL은
`website_url_source = MANUAL`입니다. `MANUAL` URL은 이후 후보 승격으로
덮어쓰지 않으며, 재크롤링에서 홈페이지가 `NULL`이 되어도 기존 URL을 삭제하지
않습니다. URL이 없는 연구실은 `website_url`과 `website_url_source`가 모두
`NULL`입니다.

이메일이 같은 후보는 교수 이름까지 같을 때 하나의 `professor`와 `laboratory`를
공유합니다. 각 학과 소속은 `professor_department`와 `laboratory_department`에 따로
저장하므로 겸임 교수도 후보를 버리지 않고 모두 승격할 수 있습니다. 대표 학과는 기존
`professor.department_id`, `laboratory.department_id`에 유지하고, 학과별 직위는
`professor_department.position`에 기록합니다. 이메일이 없으면 서로 다른 출처의
후보를 자동으로 합치지 않습니다.

같은 이메일인데 교수 이름이 다르거나, 서로 다른 공식 연구실 정보가 충돌하면 자동으로
덮어쓰지 않고 해당 후보만 실패 처리합니다. `GENERATED` 연구실과 `OFFICIAL` 연구실이
만나면 공식 이름을 우선합니다.

이미 승격된 후보가 이후 페이지에서 사라지거나 재검수에서 거절되더라도 본 데이터를
자동 삭제하지 않습니다. 본 데이터 삭제는 별도의 운영 판단으로 처리합니다.

## V15 적용 후 검수 상태를 SQL로 바꿀 때

V15를 적용하기 전에 이미 승인한 후보는 마이그레이션이 `review_revision = 1`로
자동 변환합니다. V15 적용 후 Workbench에서 새 승인을 기록할 때는 검수 세대도 반드시
1 증가시킵니다.

```sql
START TRANSACTION;

UPDATE professor_crawl_candidate
SET review_status = 'APPROVED',
    reviewed_by = '<검수자>',
    review_note = '<검수 메모>',
    reviewed_at = CURRENT_TIMESTAMP,
    review_revision = review_revision + 1,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE id = <후보_ID>
  AND is_stale = FALSE
  AND review_status = 'PENDING';

SELECT ROW_COUNT() AS approved_count;
COMMIT;
```

`approved_count`가 1일 때만 정상 승인입니다. 0이면 이미 검수되었거나 stale 후보이므로
현재 상태를 다시 확인합니다. `review_revision`을 올리지 않으면 재검수한 데이터가 이미
승격된 승인본과 같은 것으로 판단될 수 있습니다.

## 실행 전 확인

먼저 DB를 백업하고, 대상 학과에 `PENDING` 또는 `REJECTED` 후보가 남아 있지 않은지
확인합니다.

```sql
SELECT source_id,
       COUNT(*) AS total_count,
       SUM(review_status = 'APPROVED' AND is_stale = FALSE) AS approved_count,
       SUM(review_status = 'PENDING' AND is_stale = FALSE) AS pending_count,
       SUM(review_status = 'REJECTED' AND is_stale = FALSE) AS rejected_count
FROM professor_crawl_candidate
GROUP BY source_id
ORDER BY source_id;
```

아직 승격하지 않았거나 새 검수본이 생긴 후보를 미리 확인합니다.

```sql
SELECT id,
       source_id,
       professor_name,
       email,
       laboratory_name,
       reviewed_at,
       review_revision,
       promoted_at,
       promoted_reviewed_at,
       promoted_review_revision
FROM professor_crawl_candidate
WHERE review_status = 'APPROVED'
  AND is_stale = FALSE
  AND reviewed_at IS NOT NULL
  AND (
      promoted_review_revision IS NULL
      OR promoted_review_revision <> review_revision
  )
ORDER BY source_id, id;
```

## 학과 한 곳만 실행

프로젝트 루트에서 DB 환경 변수를 설정한 뒤 다음 명령을 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=prod,promotion --app.candidate-promotion.enabled=true --app.candidate-promotion.source-id=<출처_ID>"
```

`<출처_ID>`는 실제 `crawl_source.id` 숫자로 바꿉니다. 실행 전에 Flyway가 V16까지
적용하여 승격 이력과 다학과 소속 관계 테이블을 준비합니다.

## 승인 후보 전체 실행

학과 한 곳의 결과를 확인한 뒤 `source-id`를 빼면 모든 출처의 승인 후보를
처리합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=prod,promotion --app.candidate-promotion.enabled=true"
```

후보 한 명마다 별도 트랜잭션을 사용합니다. 한 후보가 이메일 또는 연구실 이름
충돌로 실패해도 다른 후보는 계속 처리하며, 마지막에 실패가 하나라도 있으면
프로세스는 실패 상태로 종료됩니다.

## 실행 결과 확인

```sql
SELECT c.id AS candidate_id,
       c.professor_name AS candidate_name,
       c.promoted_at,
       c.promoted_reviewed_at,
       c.review_revision,
       c.promoted_review_revision,
       p.id AS professor_id,
       p.name AS professor_name,
       l.id AS laboratory_id,
       l.name AS laboratory_name,
       l.name_source,
       l.recruitment_status,
       pd.department_id AS professor_department_id,
       pd.position AS department_position,
       ld.department_id AS laboratory_department_id
FROM professor_crawl_candidate c
JOIN crawl_source s ON s.id = c.source_id
LEFT JOIN professor p ON p.id = c.promoted_professor_id
LEFT JOIN laboratory l ON l.id = c.promoted_laboratory_id
LEFT JOIN professor_department pd
       ON pd.professor_id = p.id
      AND pd.department_id = s.department_id
LEFT JOIN laboratory_department ld
       ON ld.laboratory_id = l.id
      AND ld.department_id = s.department_id
WHERE c.source_id = <출처_ID>
ORDER BY c.id;
```

정상 승격된 후보는 `promoted_at`, `promoted_reviewed_at`, `professor_id`,
`laboratory_id`, `promoted_review_revision`이 채워집니다. 검수 세대 번호를 비교하므로
같은 초 안에 다시 검수하더라도 새 승인본을 놓치지 않습니다. 오래된 소프트 삭제 연구실이 물리 삭제되면 이력 보존을
위해 후보의 `promoted_laboratory_id`만 `NULL`이 될 수 있습니다. 이 경우 승격 기능은
연구실을 자동으로 되살리지 않고 충돌로 보고합니다.
