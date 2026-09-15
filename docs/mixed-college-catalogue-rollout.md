# 다중 단과대 교수·연구실 배포 데이터 (#76)

## 범위와 결과

2026-09-15 사용자가 제공한 `크롤링.csv`의 공식 교수 목록 30개를 수집했다.
교수 정보 검수·승인, 교수/연구실 승격, 개발서버 배포용 데이터까지 포함한다.
연구 소개 원문은 `laboratory.description`에 저장하지만 **세부 연구 분야 추출·승인·카테고리 연결은 이번 범위가 아니다.**

| 단과대 | 출처별 교수 후보 수 |
| --- | ---: |
| 공과대학 | 158 |
| 경영경제대학 | 59 |
| 인문과학대학 | 32 |
| 사회과학대학 | 20 |
| 인공지능융합대학(디자인·만화애니메이션) | 16 |
| 호텔관광대학 | 47 |
| 합계 | 332 |

- 332건 검수·APPROVED·승격 완료. 검수자 표기는 `codex-assisted-review/issue-76`.
- 겸임 소속을 합친 교수/연구실은 각각 303개. 기존 교수 3명은 재사용하므로 로컬 신규 생성은 각각 300개.
- 로컬 본 테이블은 교수 304 → 604, 연구실 304 → 604.
- 배포 데이터의 학과 연결은 335개: 이번 출처의 332개와 기존 교수 3명의 원래 주 소속.
- 연구실 공식 이름을 확인할 수 없는 303개는 `<교수명> 교수님 연구실`, `GENERATED`를 사용한다.
- 이메일 미공개 3명, 연구 소개 미공개 28개, 유효한 홈페이지 미확인 167개는 NULL을 유지한다. 임의로 보완하지 않는다.

## 크롤러 변경

기존 학과 HTML 및 우주항공 파서는 유지한다. 학교 대표 홈페이지의 동적 교수 목록만 새 수집 경로를 사용한다.

- `ProfessorPageFetcherRouter`: 공식 대표 홈페이지 경로와 기존 정적 수집 경로 선택.
- `SejongMainProfessorPageFetcher`: 익명 세션으로 페이지와 공개 교수 API를 요청. 쿠키는 메모리에만 유지.
- `SejongMainProfessorPageAdapter`: 페이지의 학과/CMS 코드 해석과 API 응답을 기존 파서용 문서로 변환.
- 외부 도메인·리다이렉트·HTTP 오류·과도한 응답 크기·본문 시간 초과를 차단한다.
- 비정상 JSON, 빈 교수 목록, 누락된 이름, 숫자형 연구 소개는 실패 처리한다. 오류 응답을 빈 정상 목록으로 처리하지 않는다.
- 연구 분야 문장의 의미 분류나 자동 오타 수정 기능은 추가하지 않았다. 이번 보정은 검수 결과에 한정한다.

## 12건의 검수 보정

| 대상 | 보정 |
| --- | --- |
| 함동철(2개 학과) | 이메일 후행 쉼표 제거. 학과 안내 페이지를 가리키는 홈페이지 값은 NULL |
| 김영신 | 이메일 주소가 상대 홈페이지 경로로 변환된 값은 NULL |
| 이동영 | `N/A`가 상대 홈페이지 경로로 변환된 값은 NULL |
| 노준성·김종성·오재영 | 연구실 페이지가 아닌 학교/학과 안내 홈페이지 값은 NULL |
| 서영수 | `nanofibil` → `nanofibril`, `Quamtum` → `Quantum` |
| 이수준 | `빅데이타` → `빅데이터` |
| 전민경 | `메니지먼트` → `매니지먼트` |
| 김민지 | `메세지` → `메시지` |
| 민자경 | 중복 구분 쉼표 제거 |

원문 스냅샷과 변경 전후 검수 기록은 로컬에 보관한다. CSV, DB 백업, 인증 정보는 Git에 포함하지 않는다.

학과 이름은 공식 페이지 본문과 #77의 학사 목록에 맞춰 `영어데이터융합학` → `영어데이터융합전공`,
`항공시스템공학전공` → `항공시스템공학과`로 통일했다. 로컬 학과 ID 35·31은 그대로 유지한다.
원본 CSV 파일은 수정하지 않았으며, 재수집 시 이 두 입력 이름을 정식 명칭으로 대응해야 한다.
근거: [영어데이터융합 소개](https://www.sejong.ac.kr/kor/college/english-language-and-literature.do),
[항공시스템 교수 목록](https://ae.sejong.ac.kr/shop_contents/myboard_list.htm?myboard_code=professor&category_idx=82051).

## V44 배포 방식

**머지 순서: PR #77(V42·V43) → 이 PR(V44).** 사용자가 이 순서로 진행하기로 확인했다.
V44를 먼저 적용한 DB에 낮은 버전을 나중에 끼워 넣지 않는다. 로컬은 승인·승격을 완료했지만 V44 Flyway 등록은 #77 적용 이후로 둔다.

`V44__import_reviewed_mixed_college_professors.sql`은 실제 서비스용 검수 데이터다. 로컬 테스트 seed가 아니다.

1. 단과대·학과와 크롤링 출처가 없으면 추가한다.
2. 이메일로 교수 ID를 찾는다. 이메일이 NULL이면 단과대·학과·이름과 NULL 이메일 조건을 함께 사용한다.
3. 없는 교수·연구실만 추가하고, 기존 ID와 프로필·공식 연구실 이름·모집 상태는 그대로 둔다.
4. 없는 교수–학과, 연구실–학과 연결만 추가한다.
5. 미연결을 검사한 뒤 이번 마이그레이션의 staging 테이블을 제거한다.

SQL의 `record_key`는 파일 안에서만 쓰는 임시 연결 번호다. 로컬 DB의 PK를 서버에 강제로 넣지 않는다.
같은 이메일의 다른 이름, 같은 학과/이름의 다른 이메일, 중복된 NULL 이메일 신원, 복수 활성 연구실, 삭제된 연구실만 있는 경우, 출처의 학과/파서 불일치는 추측해 병합하지 않고 실패시킨다.

서버에는 본 테이블과 출처 메타데이터만 배포한다. 로컬 검수 후보·검수 이력·사용자 계정·북마크·토큰·연구 분야 테이블은 복제하거나 변경하지 않는다.
빈 DB에서는 이 배포분의 교수/연구실 303개가 생긴다. **기존 DB의 최종 총개수는 서버에 이미 있는 데이터에 따라 다르며 반드시 604개인 것은 아니다.**

## 검증 및 배포

2026-09-15 실행 결과:

- 전체 테스트: 503개 통과, 실패 0개, Docker 등 환경 조건으로 30개 건너뜀.
- 최종 학과명 보정본: 크롤러·H2·MySQL 관련 33개 테스트 재실행 통과.
- 배포 스크립트 단위 테스트: 26개 통과.
- #77의 `5132cbbbb19fb398d72d8bbb346b3baa2c1b71ff` 마이그레이션을 먼저 적용한 MySQL에서 V44 적용 성공.
  기존 단과대 9개·학과 64개·커뮤니티 그룹은 바뀌지 않고 교수/연구실 303개·소속 연결 335개가 추가됨.
- 로컬 검수 후보 332개 모두 승격됐으며 교수/연구실 소속 누락과 소유 교수 불일치가 각각 0개.

- H2와 실제 MySQL 8.0에서 빈 DB 전체 마이그레이션, V41 업그레이드, 재실행 중복 방지, 기존 데이터 보존, 충돌 거부, Hibernate validate를 검사한다.
- API 테스트의 데이터는 테스트 트랜잭션 안에서 격리한다. 배포 데이터의 정확한 개수·관계는 별도 마이그레이션 계약 테스트가 검증한다.
- 로컬 승인·승격 상태와 본 테이블 연결을 확인한다. V44 자체의 적용·보존 검사는 별도 MySQL에서 수행한다.
- PR 머지 후 develop CI가 테스트·이미지 검증에 성공하면 새 개발 이미지를 발행한다. 구성된 서버 pull 배포가 이미지를 감지하면 DB 백업 후 앱 기동 시 Flyway가 적용한다.
- 이 PR은 서버의 배포 설정이나 비밀값을 바꾸지 않으며 자동 머지하지 않는다. 서버 배포 완료 여부는 별도 확인한다.

MySQL DDL은 전체 롤백되지 않는다. 단과대/학과/staging 준비 중 실패하면 일부 작업이 남을 수 있다.
이번 파일은 기존 본 데이터를 삭제/덮어쓰지 않으며 재실행 시 이미 존재하는 행을 다시 만들지 않는다.
다만 실패를 무시하고 무조건 `repair`하지 않는다. 충돌 원인, 백업, 부분 적용 상태를 확인한 후 복구 절차를 결정한다.
마이그레이션 변경이 있는 배포 실패는 기존 배포 스크립트 정책대로 수동 복구 확인이 필요하다.

## 로컬 승인·승격 확인 SQL

아래 쿼리는 로컬 검수 DB 전용이다. 서버에는 후보 이력을 배포하지 않으므로 서버에서 332가 나와야 하는 쿼리가 아니다.

```sql
SELECT COUNT(*) AS reviewed_candidates,
       SUM(review_status = 'APPROVED') AS approved_candidates,
       SUM(promoted_professor_id IS NOT NULL
           AND promoted_laboratory_id IS NOT NULL
           AND promoted_review_revision = review_revision) AS promoted_candidates,
       COUNT(DISTINCT promoted_professor_id) AS distinct_professors,
       COUNT(DISTINCT promoted_laboratory_id) AS distinct_laboratories
FROM professor_crawl_candidate
WHERE reviewed_by = 'codex-assisted-review/issue-76';
```

기대값: `332 / 332 / 332 / 303 / 303`.

## 본 테이블 확인 SQL (로컬·서버 공통)

```sql
SELECT c.name AS college_name, d.name AS department_name,
       p.id AS professor_id, p.name AS professor_name, p.email,
       pd.position, l.id AS laboratory_id, l.name AS laboratory_name,
       l.name_source, l.website_url, l.description
FROM laboratory l
JOIN professor p ON p.id = l.professor_id
JOIN laboratory_department ld ON ld.laboratory_id = l.id
JOIN department d ON d.id = ld.department_id
JOIN college c ON c.id = d.college_id
LEFT JOIN professor_department pd
  ON pd.professor_id = p.id AND pd.department_id = d.id
WHERE l.deleted_at IS NULL
ORDER BY c.name, d.name, p.name, l.id;
```

겸임 교수는 학과별로 여러 행에 나타나는 것이 정상이다. 교수/연구실 ID가 같고 학과만 달라진다.
