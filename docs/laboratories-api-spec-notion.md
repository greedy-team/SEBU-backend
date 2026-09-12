# 연구실 목록 조회 API 명세

각 연구실에 연구 분야 이름·연구 분야 ID·분야별 카테고리 ID를 함께 반환한다. 소속 학과도 기존 `affiliations` 배열로 반환한다.

- 개정일: 2026-09-12
- 상태: **이번 브랜치에서 구현하는 응답 계약**. 병합·운영 배포 완료를 의미하지 않는다.
- 기준: `develop` 커밋 `2a919e5`. 쿠키 인증 PR #66과 계정 복구 PR #69가 반영된 기존 API에 연구 분야 상세를 추가한다.
- 예시 ID·연락처·연구 분야 연결 관계는 설명용이며 운영 DB 값이 아니다.
- [상세 명세](laboratories-api-spec.md) · [복사용 전체 JSON](laboratories-response.example.json)

## 공통 요청 규칙

- FE는 동일 출처 `/api`로 요청한다. fetch는 `credentials: "include"`, Axios는 `withCredentials: true`를 사용한다.
- 인증은 선택이다. 로그인 시 브라우저가 HttpOnly `access_token` 쿠키를 자동 전송한다. 인증 쿠키가 없으면 비로그인 조회가 가능하며 `bookmarked`는 `false`다.
- GET 조회에는 요청 JSON 본문과 CSRF 헤더가 필요 없다. Bearer 헤더를 직접 구성하지 않는다.
- 잘못되거나 만료된 Access 쿠키가 실제로 전송되면 401이 발생할 수 있다.
- 로그인·갱신·로그아웃의 요청·응답은 [쿠키 인증 계약](cookie-authentication.md)을 따른다. 현재 로그인 JSON은 `loginStatus`로 로그인 완료와 복구 필요를 구분한다. [회원 탈퇴 및 계정 복구 계약](account-withdrawal-recovery.md)도 함께 참고한다.

## 1. 전체 랩실 조회

**요청**

```http
GET /api/v1/laboratories
Accept: application/json
```

기본 전체 조회의 요청 파라미터·본문 없음. 로그인 상태라면 `access_token` 쿠키가 자동 전송된다.

**응답 — 200 OK**

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
            "categoryIds": [2]
          },
          {
            "researchFieldId": 102,
            "name": "머신러닝",
            "categoryIds": [1, 2]
          },
          {
            "researchFieldId": 103,
            "name": "신규 연구 분야",
            "categoryIds": []
          }
        ],
        "researchFieldCategoryIds": [1, 2],
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

**필드 규칙**

- `researchFieldDetails`: 해당 랩실에 연결된 연구 분야만 포함.
- `researchFieldId`: 연구 분야 고유 ID.
- `name`: 연구 분야 이름.
- `categoryIds`: 해당 연구 분야가 속한 카테고리 ID 배열. 복수 연결 가능, 미분류이면 `[]`.
- `researchFieldCategoryIds`: 랩실에 연결된 모든 분야의 카테고리 ID를 중복 제거한 배열.
- `researchFieldCategories`: 위 카테고리 ID에 해당하는 코드·이름. 분야와 카테고리는 배열 위치가 아닌 ID로 연결.
- `researchFields`: 기존 이름 배열 유지. `researchFieldDetails[].name`과 같은 순서.
- 분야가 없는 랩실은 연구 분야·카테고리 관련 배열을 모두 `[]`로 반환.
- `bookmarked`: 로그인 사용자의 북마크 여부. 인증 쿠키 없는 비로그인 요청도 허용하며 이때는 `false`.
- `sort=REVIEW_COUNT_DESC&page=0&size=20` 조회에도 같은 연구실 객체를 사용한다. 해당 경우 `data`에 기존 `page`, `size`, `totalElements`, `hasNext`가 추가된다.

## 2. 후기 수 내림차순 페이지 조회

**요청**

```http
GET /api/v1/laboratories?sort=REVIEW_COUNT_DESC&page=0&size=20
Accept: application/json
```

요청 본문 없음. `page`는 0 이상, `size`는 1~50이며 생략 시 각각 `0`, `20`이다. 후기 수가 같으면 연구실 ID 내림차순으로 정렬한다. 삭제된 연구실과 삭제된 후기는 집계에서 제외한다.

`sort`가 `REVIEW_COUNT_DESC`가 아니거나 생략되면 전체 조회를 수행한다. 연구 분야·카테고리 필터 파라미터는 없다.

**응답 — 200 OK**

각 `laboratories[]` 항목은 1절의 전체 조회와 같은 객체이며 `researchFieldDetails`도 포함한다. 페이지 응답에는 아래처럼 `page`, `size`, `totalElements`, `hasNext`가 추가된다. 아래는 조회할 연구실이 없는 경우의 완전한 JSON이다.

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

`totalElements`는 전체 활성 연구실 수이며 `hasNext`는 다음 페이지 유무다. 요청한 페이지 범위를 벗어나도 전체 연구실이 존재하면 `totalElements`는 그 수를 유지한다.

## 3. 소속 정보

`college`·`department`는 기존 대표 소속이고 `affiliations`는 소속 목록이다.

- 단일 소속이면 1절 JSON처럼 배열 항목 1개를 반환한다.
- 복수 소속이면 아래처럼 각 소속을 배열에 담는다.
- 소속 연결 조회 결과가 비어도 기존 대표 `college`·`department`로 배열 항목 1개를 구성한다.
- 대표 학과가 연결 목록에 있으면 첫 번째에 오고, 나머지는 학과 ID 오름차순이다.

복수 소속의 관련 필드만 발췌한 JSON:

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

## 4. 연구 분야의 빈 값

분야가 없는 연구실도 목록에 포함되며, 관련 필드는 다음과 같다.

```json
{
  "researchFields": [],
  "researchFieldDetails": [],
  "researchFieldCategoryIds": [],
  "researchFieldCategories": []
}
```

분야는 있지만 카테고리가 연결되지 않았다면 이름과 연구 분야 ID를 유지한다. 아래 JSON은 관련 필드만 발췌한 예시다.

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

연구 분야·카테고리 배열은 `null` 또는 필드 생략 대신 `[]`를 사용한다. 미분류 ID로 `0`을 넣지 않는다.

## 5. 필드 연결 규칙

- 연구실마다 연결된 분야만 포함하고 `researchFieldId`는 중복하지 않는다. 같은 분야가 여러 연구실에 연결되면 동일한 분야 ID를 각 연구실에서 반환한다.
- `researchFields`는 `researchFieldDetails[].name`과 같은 내용·같은 순서이며 기존 이름순 정렬을 유지한다.
- 각 `categoryIds`는 중복 없이 카테고리 표시 순서(`display_order`), ID 순으로 정렬한다.
- 랩실 수준 `researchFieldCategoryIds`는 분야별 `categoryIds`의 중복 없는 합집합이며 같은 카테고리 정렬을 따른다.
- `researchFieldCategories`의 ID와 순서는 `researchFieldCategoryIds`와 일치한다. 분야와 카테고리는 배열 위치가 아니라 ID로 연결한다.
- 기존 문자열 배열·카테고리 배열·소속 배열과 나머지 응답 필드는 유지한다. 이번 추가 필드는 전체 조회와 후기 수 정렬 페이지 조회에 적용되며 북마크·마이페이지의 별도 요약 응답은 변경하지 않는다.
