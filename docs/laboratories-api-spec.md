# 연구실 목록 조회 API 명세 — 연구 분야 상세

각 연구실에 `researchFieldDetails`를 추가하여 연구 분야 이름, 연구 분야 ID, 해당 분야의 카테고리 ID 목록을 함께 반환한다.

- 개정일: 2026-09-12
- 상태: **이번 브랜치에서 구현하는 응답 계약**. 병합·운영 배포 완료를 의미하지 않는다.
- 기준: `develop` 커밋 `2a919e5`의 기존 응답에 연구 분야 상세를 추가한다. 쿠키 인증 PR #66과 계정 복구 PR #69는 이미 `develop`에 반영되어 있다.
- 기존 `researchFields: string[]`, `researchFieldCategoryIds`, `researchFieldCategories`, `affiliations`와 그 외 응답 필드를 유지한다.
- 적용 API: 전체 조회와 후기 수 내림차순 페이지 조회. 북마크·마이페이지의 별도 연구실 요약 응답은 이번 변경 대상이 아니다.
- 복사·파싱용 전체 JSON: [laboratories-response.example.json](laboratories-response.example.json)
- 노션 복사용 요청·응답 요약: [laboratories-api-spec-notion.md](laboratories-api-spec-notion.md)

## 1. 요청

### 1.1. 공통 요청 및 인증

- 인증은 선택이다. 인증 쿠키가 없는 비로그인 요청도 허용하며 이때 `bookmarked`는 `false`다.
- 로그인 상태에서는 브라우저가 HttpOnly `access_token` 쿠키를 전송하고, BE가 사용자별 `bookmarked`를 계산한다.
- FE는 동일 출처 `/api` 프록시로 요청하며 fetch는 `credentials: "include"`, Axios는 `withCredentials: true`를 사용한다. Bearer 헤더를 직접 구성하지 않는다.
- 두 GET 조회에는 요청 JSON 본문과 `X-XSRF-TOKEN` 헤더가 필요 없다.
- 잘못되거나 만료된 `access_token`이 실제로 전송되면 공개 조회라도 401이 발생할 수 있다.
- 로그인·갱신·로그아웃의 요청, 쿠키·CSRF·만료 처리와 JSON은 [쿠키 인증 계약](cookie-authentication.md)을 따른다. 로그인 응답은 `loginStatus`로 로그인 완료와 복구 필요를 구분한다. 복구 절차는 [회원 탈퇴 및 계정 복구 계약](account-withdrawal-recovery.md)을 참고한다.

### 1.2. 전체 조회

```http
GET /api/v1/laboratories
Accept: application/json
```

요청 파라미터·본문 없음. 소프트 삭제된 연구실을 제외한 전체 목록을 연구실 ID 오름차순으로 반환한다. 성공 응답은 `200 OK`, `Content-Type: application/json`이며 3절의 JSON을 사용한다.

### 1.3. 후기 수 내림차순 페이지 조회

```http
GET /api/v1/laboratories?sort=REVIEW_COUNT_DESC&page=0&size=20
Accept: application/json
```

요청 본문 없음. 후기 수 내림차순으로 정렬하고, 후기 수가 같으면 연구실 ID 내림차순으로 정렬한다. 삭제된 연구실과 삭제된 후기는 집계에서 제외한다.

| 파라미터 | 타입 | 기본값 | 동작 |
| --- | --- | --- | --- |
| `sort` | string | 미지정 | `REVIEW_COUNT_DESC`이면 후기 수 내림차순 페이지 조회. 그 외 값 또는 미지정이면 전체 목록 조회 |
| `page` | integer | `0` | `sort=REVIEW_COUNT_DESC`일 때만 사용. 0 이상 |
| `size` | integer | `20` | `sort=REVIEW_COUNT_DESC`일 때만 사용. 1~50 |

페이지 응답의 각 연구실 객체는 전체 조회와 동일하며 `researchFieldDetails`를 포함한다. `data`에는 `laboratories`, `page`, `size`, `totalElements`, `hasNext`가 있다. `totalElements`는 전체 활성 연구실 수이고 `hasNext`는 다음 페이지 유무다. 연구 분야·카테고리 필터 파라미터는 추가하지 않는다.

## 2. 연구 분야 관련 필드

아래 경로는 모두 `data.laboratories[]`를 기준으로 한다.

| 필드 | JSON 타입 | 상태 | 의미 |
| --- | --- | --- | --- |
| `researchFields` | string[] | 유지 | 해당 연구실에 연결된 연구 분야 이름 목록 |
| `researchFieldDetails` | object[] | **추가** | 해당 연구실의 연구 분야별 식별 정보와 카테고리 연결 |
| `researchFieldDetails[].researchFieldId` | integer | **추가** | `research_field.id`. null이 아닌 연구 분야 고유 ID |
| `researchFieldDetails[].name` | string | **추가** | `research_field.name`. 해당 연구 분야 이름 |
| `researchFieldDetails[].categoryIds` | integer[] | **추가** | 해당 연구 분야에 연결된 `research_field_category.id` 목록 |
| `researchFieldCategoryIds` | integer[] | 유지 | 해당 연구실의 모든 연구 분야에서 모은 카테고리 ID의 중복 없는 목록 |
| `researchFieldCategories` | object[] | 유지 | 해당 연구실에 해당하는 카테고리 정보의 중복 없는 목록 |
| `researchFieldCategories[].id` | integer | 유지 | 카테고리 ID. `categoryIds[]`에서 참조하는 값 |
| `researchFieldCategories[].code` | string | 유지 | 카테고리 코드 |
| `researchFieldCategories[].name` | string | 유지 | 카테고리 표시 이름 |

ID는 기존 API와 동일하게 JSON 숫자로 전달하며, 백엔드 타입은 `Long`이다. 예시의 ID·이름·연결 관계·연락처는 설명용 데이터이며 운영 DB 값이 아니다.

### 2.1. 기존 소속 필드 `affiliations`

`college`와 `department`는 기존 대표 소속이고, `affiliations`는 해당 연구실의 소속 목록이다. 소속이 하나이면 배열 항목 1개를 반환하며, 소속이 여러 개이면 각각 `{ college, department }` 항목으로 반환한다. 소속 연결 조회 결과가 비어 있어도 기존 대표 `college`·`department`로 항목 1개를 구성한다.

대표 학과가 연결 목록에 있으면 첫 번째에 오고, 나머지는 학과 ID 오름차순이다. 소속 배열은 연구 분야 카테고리와 별개이며 학과를 기준으로 카테고리 ID를 추정하지 않는다.

복수 소속 연구실의 관련 필드만 발췌한 JSON:

```json
{
  "college": { "id": 1, "name": "인공지능융합대학" },
  "department": { "id": 3, "name": "컴퓨터공학과" },
  "affiliations": [
    {
      "college": { "id": 1, "name": "인공지능융합대학" },
      "department": { "id": 3, "name": "컴퓨터공학과" }
    },
    {
      "college": { "id": 1, "name": "인공지능융합대학" },
      "department": { "id": 4, "name": "정보보호학과" }
    }
  ]
}
```

## 3. 전체 조회 성공 응답 JSON

```json
{
  "success": true,
  "data": {
    "laboratories": [
      {
        "id": 31,
        "name": "예시 지능정보 연구실",
        "nameSource": "OFFICIAL",
        "websiteUrl": "https://example.com/laboratories/31",
        "professor": {
          "id": 11,
          "name": "예시 교수",
          "email": "professor@example.com"
        },
        "college": {
          "id": 1,
          "name": "인공지능융합대학"
        },
        "department": {
          "id": 3,
          "name": "컴퓨터공학과"
        },
        "affiliations": [
          {
            "college": {
              "id": 1,
              "name": "인공지능융합대학"
            },
            "department": {
              "id": 3,
              "name": "컴퓨터공학과"
            }
          }
        ],
        "researchFields": [
          "데이터마이닝",
          "머신러닝",
          "신규 연구 분야"
        ],
        "researchFieldDetails": [
          {
            "researchFieldId": 101,
            "name": "데이터마이닝",
            "categoryIds": [
              2
            ]
          },
          {
            "researchFieldId": 102,
            "name": "머신러닝",
            "categoryIds": [
              1,
              2
            ]
          },
          {
            "researchFieldId": 103,
            "name": "신규 연구 분야",
            "categoryIds": []
          }
        ],
        "researchFieldCategoryIds": [
          1,
          2
        ],
        "researchFieldCategories": [
          {
            "id": 1,
            "code": "AI_ML",
            "name": "인공지능·기계학습"
          },
          {
            "id": 2,
            "code": "DATA_INFORMATION",
            "name": "데이터과학·정보관리"
          }
        ],
        "recruitmentStatus": "UNKNOWN",
        "bookmarkCount": 0,
        "bookmarked": false,
        "reviewCount": 0
      }
    ]
  },
  "error": null
}
```

예시에서 연구 분야 `101`은 카테고리 `2`에, 연구 분야 `102`는 카테고리 `1`과 `2`에 속한다. 연구 분야 `103`은 등록되어 있지만 카테고리가 아직 연결되지 않은 상태다.

## 4. 응답 일관성 규칙

1. `researchFieldDetails`에는 **해당 연구실에 연결된 연구 분야만** 포함한다. 전체 연구 분야 사전이나 다른 연구실의 분야를 포함하지 않는다.
2. 연구 분야 하나에 카테고리를 0개 이상 연결할 수 있으므로 단일 `categoryId` 대신 `categoryIds` 배열을 사용한다.
3. 각 연구실 내에서 `researchFieldId`는 중복되지 않는다. 동일한 연구 분야가 여러 연구실에 속하면 같은 ID를 사용하되 연구실마다 해당 항목을 반환한다.
4. `researchFields`는 `researchFieldDetails`의 `name`을 같은 순서로 나열한 결과와 일치해야 한다. 기존 이름 기준 정렬을 유지한다.
5. 각 `categoryIds`는 중복 없이 카테고리 표시 순서(`display_order`), ID 순으로 정렬한다.
6. 랩실 수준 `researchFieldCategoryIds`는 모든 `researchFieldDetails[].categoryIds`의 합집합이다. 중복을 제거하고 카테고리 표시 순서, ID 순으로 정렬한다.
7. `researchFieldCategories`는 `researchFieldCategoryIds`와 같은 순서·같은 ID 집합을 갖는다. 분야별 `categoryIds`에 등장한 ID는 반드시 이 목록에 존재한다.
8. 연구 분야와 카테고리 관련 배열은 값이 없을 때 `[]`를 반환한다. `null`이나 필드 생략으로 표현하지 않는다.
9. 카테고리가 없는 연구 분야도 이름과 ID를 포함한다. 임의의 카테고리 ID나 `0`을 미분류 표시로 사용하지 않는다.

카테고리 관계는 `laboratory_research_field.research_field_id → research_field_category_mapping.research_field_id → research_field_category_mapping.category_id`를 기준으로 한다. 연구실 소속 학과로 카테고리를 추정하지 않는다.

## 5. 빈 값 사례

연구 분야가 없는 연구실도 `laboratories`에 포함하며, 그 연구실의 연구 분야 관련 필드들은 다음과 같다. 아래 JSON은 전체 응답 중 관련 필드만 발췌한 예시다.

```json
{
  "researchFields": [],
  "researchFieldDetails": [],
  "researchFieldCategoryIds": [],
  "researchFieldCategories": []
}
```

연구 분야는 있지만 연결된 카테고리가 전혀 없는 연구실의 관련 필드 예시:

```json
{
  "researchFields": ["신규 연구 분야"],
  "researchFieldDetails": [
    {
      "researchFieldId": 103,
      "name": "신규 연구 분야",
      "categoryIds": []
    }
  ],
  "researchFieldCategoryIds": [],
  "researchFieldCategories": []
}
```

조회할 연구실 자체가 없을 때의 전체 응답:

```json
{
  "success": true,
  "data": {
    "laboratories": []
  },
  "error": null
}
```

후기 수 정렬 페이지 조회에서 연구실이 하나도 없는 경우의 전체 응답:

```json
{
  "success": true,
  "data": {
    "laboratories": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "hasNext": false
  },
  "error": null
}
```

데이터가 있는 페이지의 `laboratories[]`에는 3절과 동일한 연구실 객체가 들어간다. 전체 연구실은 존재하지만 요청한 페이지 범위를 벗어난 경우에도 `laboratories`는 `[]`이며, `totalElements`는 전체 연구실 수를 유지한다.

## 6. 프론트엔드 사용

- 연구 분야 표시와 선택값 관리: `researchFieldDetails[].name`, `researchFieldDetails[].researchFieldId`.
- 선택한 카테고리에 해당하는 연구 분야 표시: 각 항목의 `categoryIds`에 선택한 카테고리 ID가 포함되는지 확인.
- 연구실의 카테고리 배지 표시: `researchFieldCategories[].name`.
- 연구실 단위 카테고리 필터: `researchFieldCategoryIds`에 선택한 카테고리 ID가 포함되는지 확인.
- 기존 이름만 표시하는 화면: `researchFields`를 계속 사용할 수 있다.

분야 배열과 카테고리 배열의 인덱스가 같다고 연결하지 않는다. 반드시 분야별 `categoryIds`와 `researchFieldCategories[].id`로 연결한다. 전체 카테고리 선택지는 기존 `GET /api/v1/research-field-categories`를 사용한다.

## 7. 검증 계약

- 전체 조회와 후기 수 정렬 페이지 조회에 모두 `researchFieldDetails`가 포함된다.
- 단일 카테고리, 복수 카테고리, 카테고리 미지정, 연구 분야 없음, 연구실 없음 사례가 위 JSON 계약을 만족한다.
- 기존 필드의 타입과 의미를 유지한다. 특히 `researchFields`를 객체 배열로 바꾸지 않는다.
- 연구 분야 조회·응답 DTO와 자동 생성 OpenAPI 스키마에 동일한 필드명과 타입을 적용한다.
- 연구실 소속이 하나여도 `affiliations` 배열을 반환하며, 복수 소속과 기본 소속 대체 규칙을 유지한다.

## 8. 기준 소스

- [연구실 컨트롤러](../src/main/java/com/sebu/backend/laboratory/controller/LaboratoryController.java)
- [연구실 응답 DTO](../src/main/java/com/sebu/backend/laboratory/dto/LaboratoriesResponse.java)
- [연구실 조회 서비스](../src/main/java/com/sebu/backend/laboratory/service/LaboratoryQueryService.java)
- [연구실 요약 조립](../src/main/java/com/sebu/backend/laboratory/query/LaboratorySummaryAssembler.java)
- [쿠키 인증 계약](cookie-authentication.md), [회원 탈퇴 및 계정 복구 계약](account-withdrawal-recovery.md)
