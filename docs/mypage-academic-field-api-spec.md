# 마이페이지 전공 계열 선택 API 명세

기존 **프로필 저장 API에 `academicField`를 추가**하고, 저장 응답과 마이페이지 조회 응답에 같은 값을 반환한다. 계열은 7개 중 하나를 선택한다.

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-09-12 |
| 기준 프로젝트 | `greedy-team/SEBU-backend` |
| 구현 기준 | `develop`의 `2a919e5`에서 `feat/mypage-academic-field` 브랜치로 구현 |
| 최신 PR 대조 | [PR #71](https://github.com/greedy-team/SEBU-backend/pull/71), head `d933ca2` / base `2a919e5`, 2026-09-12 확인 당시 OPEN |
| 문서 상태 | **계열 선택 기능 구현 명세. 이 브랜치에서 저장·조회·검증을 구현했다.** |
| 기존 동작 | 프로필 저장·마이페이지 조회, 쿠키 인증, CSRF 검증 |
| 이번 구현 대상 | 계열 입력 검증, DB 저장, 저장·조회 응답 필드 추가 |

이 문서의 JSON은 **이 브랜치에서 구현한 요청·응답 계약**이다. 이름·학과·ID·날짜는 예시 값이다. 계열은 저장 요청에서 필수이며 기존 사용자 조회에서는 null을 허용한다. 실제 API 사용 가능 시점은 이 브랜치의 배포 이후다.

**PR #71 대조 결과:** 기존 성공·오류 응답의 공통 구조와 마이페이지 응답 경로는 이 명세와 일치한다. `academicField`와 계열별 검증 오류는 이 브랜치에서 구현한 신규 기능이며 PR #71 자체에는 포함되어 있지 않다. PR에서 추가한 `researchFieldDetails`는 연구실 목록용 정보다. 상세 비교는 11절을 참고한다.

## 1. API 요약

| Method | URL | 요청에서 추가할 값 | 응답에서 추가할 값 |
| --- | --- | --- | --- |
| `PUT` | `/api/v1/users/me/profile` | `academicField` | `data.academicField` |
| `GET` | `/api/v1/users/me/mypage` | 없음 | `data.profile.academicField` |

- 계열만 저장하는 별도 API를 만들지 않고, 기존 프로필 저장 요청에 함께 보낸다.
- 선택지는 고정된 7개이므로 프론트엔드에서 아래 코드·이름 목록을 사용한다. 계열 목록 조회 API는 이번 범위에 포함하지 않는다.
- 학과는 기존 응답의 `department`를 사용한다. 학사정보에서 가져오는 읽기 전용 값이며, 계열 선택으로 학과를 변경하지 않는다.
- `GET /api/v1/me`, 학년 수정용 `PATCH /api/v1/me/profile`, 커뮤니티 공개 프로필의 응답은 이번 명세에서 확장하지 않는다.

## 2. 계열 코드

JSON 필드명은 **`academicField`**다. 요청·응답 모두 아래 영문 코드 하나를 문자열로 사용하고, 한글 표시는 프론트엔드에서 변환한다.

| 표시 순서 | 화면 표시 이름 | JSON 저장 값 |
| --- | --- | --- |
| 1 | 인문계열 | `HUMANITIES` |
| 2 | 사회·경영계열 | `SOCIAL_BUSINESS` |
| 3 | 교육계열 | `EDUCATION` |
| 4 | 자연과학계열 | `NATURAL_SCIENCE` |
| 5 | 공학계열 | `ENGINEERING` |
| 6 | 예체능계열 | `ARTS_SPORTS` |
| 7 | 기타·미정 | `OTHER_UNDECIDED` |

의약·보건계열은 제공하지 않는다. 배열, 숫자, 한글 이름, 소문자 코드, 앞뒤 공백이 붙은 코드도 허용하지 않는다.

| 값/상태 | 의미 | 저장 요청 허용 |
| --- | --- | --- |
| `"ENGINEERING"` 등 위 코드 | 사용자가 선택한 계열 | 허용 |
| `"OTHER_UNDECIDED"` | 사용자가 직접 선택한 기타·미정 | 허용 |
| `null` | 기존 사용자가 아직 계열을 선택하지 않음 | 조회에서만 허용 |
| 필드 누락, `""`, `"   "` | 계열을 선택하지 않은 요청 | 허용하지 않음 |

`null`을 `OTHER_UNDECIDED`로 자동 변환하거나 기본 선택하지 않는다. `department`나 연구 관심 분야에서 계열을 자동 추론해 저장하지 않는다.

## 3. 공통 요청·응답 규칙

### 인증과 CSRF

현재 프로젝트는 **HttpOnly `access_token` 쿠키로 인증**한다. `Authorization: Bearer` 헤더만 보내는 방식은 지원하지 않는다. 사용자 ID는 인증 정보에서 확인하며 요청 Body에 넣지 않는다.

| 요청 | Access 쿠키 | CSRF 헤더 | 요청 Body |
| --- | --- | --- | --- |
| `GET /api/v1/users/me/mypage` | 필수 | 불필요 | 없음 |
| `PUT /api/v1/users/me/profile` | 필수 | `X-XSRF-TOKEN` 필수 | JSON |

저장 요청은 다음 조건을 충족해야 한다.

1. 필요하면 `GET /api/v1/auth/csrf`로 `XSRF-TOKEN` 쿠키를 준비한다. 성공 응답은 `204 No Content`이며 JSON Body가 없다.
2. 현재 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보낸다. 로그인 뒤에는 갱신된 쿠키 값을 읽는다.
3. 허용된 `Origin` 또는 `Referer`가 있어야 한다. 둘 다 없거나 허용되지 않은 출처면 `403 CSRF_TOKEN_INVALID`다.
4. 브라우저에서는 동일 출처 `/api` 프록시와 `credentials: "include"`를 사용한다. 브라우저가 Cookie·Origin 헤더를 처리한다. 아래 HTTP 예시는 실제 전송 형태를 설명한 것이다.

상세한 로그인·쿠키·갱신 계약은 [쿠키 기반 인증 계약](cookie-authentication.md)을 따른다.

### 응답 형태

- 성공: `success: true`, `data: 결과`, `error: null`.
- 실패: `success: false`, `data: null`, `error: 오류 객체`.
- 이 문서의 두 API는 성공 시 `Content-Type: application/json`, `Cache-Control: private, no-store`를 반환한다.
- `department.id`는 문자열 또는 `null`이다. 학과 자체가 없으면 `department: null`이다.
- `profileUpdatedAt`은 기존 `LocalDateTime` 직렬화 형식을 따른다. 예: `2026-09-12T14:30:00`. 소수 초가 붙을 수 있고 UTC 오프셋이 없으므로 `Z`를 임의로 추가하지 않는다. 시간대 표준화는 이번 변경 범위에 포함하지 않는다.

## 4. 프로필·계열 저장

### `PUT /api/v1/users/me/profile`

최초 계열 선택과 이후 계열 변경에 같은 API를 사용한다. **계열만 바꿀 때도 기존 프로필의 나머지 입력값을 함께 보낸다.**

### 요청 헤더

```http
PUT /api/v1/users/me/profile HTTP/1.1
Content-Type: application/json
Accept: application/json
Cookie: access_token=<로그인으로 발급된 쿠키>; XSRF-TOKEN=<현재 CSRF 쿠키>
X-XSRF-TOKEN: <현재 CSRF 쿠키 값>
Origin: http://localhost:5173
```

`Origin`은 local 환경 예시다. 배포 환경에서는 서버 설정에 등록된 프론트엔드 출처를 사용한다.

### 요청 JSON — 공학계열 선택

```json
{
  "nickname": "세부학생",
  "grade": 3,
  "academicField": "ENGINEERING",
  "gpaBand": "GTE_3_5",
  "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다."
}
```

### 요청 필드

| 필드 | JSON 타입 | 필수 | 허용값·처리 규칙 |
| --- | --- | --- | --- |
| `nickname` | string / null | 아니오 | 기존 규칙 유지. 정규화 후 최대 30자, 중복 닉네임 불가. 누락·`null`·정규화 후 빈 문자열은 닉네임 해제. 예약어 `익명`과 금지 문자는 허용하지 않음 |
| `grade` | number (정수) | 예 | `1`, `2`, `3`, `4` |
| **`academicField`** | **string** | **예, 신규** | **2절의 코드 7개 중 하나. 누락·`null`·빈 문자열 불가** |
| `gpaBand` | string / null | 아니오 | `GTE_3_0`, `GTE_3_5`, `GTE_4_0`, `null`. 누락·`null`은 성적 선택 해제 |
| `introduction` | string | 예 | 최대 500자. 빈 문자열 `""` 허용. 누락·`null` 불가. 기존 내용 검증 적용 |

`name`, `department`, `major`, `majorId`, `academicFieldLabel`, `profileCompleted`는 수정 요청 필드가 아니다. `nickname`·`gpaBand`를 생략해도 기존 값이 보존되는 부분 수정 API로 해석하면 안 된다.

### 성공 응답 — `200 OK`

```json
{
  "success": true,
  "data": {
    "name": "홍길동",
    "nickname": "세부학생",
    "grade": 3,
    "department": {
      "id": "12",
      "name": "컴퓨터공학과"
    },
    "academicField": "ENGINEERING",
    "gpaBand": "GTE_3_5",
    "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다.",
    "profileCompleted": true,
    "profileUpdatedAt": "2026-09-12T14:30:00"
  },
  "error": null
}
```

### 저장 응답 필드

| 경로 | JSON 타입 | 의미 |
| --- | --- | --- |
| `data.name` | string / null | 학사정보 실명. 읽기 전용 |
| `data.nickname` | string / null | 저장된 공개 닉네임 |
| `data.grade` | number | 저장된 학년 |
| `data.department` | object / null | 학사정보 학과. 읽기 전용 |
| `data.department.id` | string / null | 내부 학과 ID. 학과 매핑이 없으면 `null` |
| `data.department.name` | string | 학과명. `department`가 있을 때 제공 |
| **`data.academicField`** | **string** | **저장된 계열 코드. 저장 성공 응답에서는 `null` 불가** |
| `data.gpaBand` | string / null | 저장된 성적 구간 |
| `data.introduction` | string | 저장된 자기소개 |
| `data.profileCompleted` | boolean | 기존 프로필 완료 판정. 계열 선택 여부와는 별도이며 6절 참고 |
| `data.profileUpdatedAt` | string / null | 최근 프로필 변경 시각. 기존 필드의 nullable 계약 유지 |

### 요청 JSON — 기타·미정 선택

```json
{
  "nickname": "세부학생",
  "grade": 3,
  "academicField": "OTHER_UNDECIDED",
  "gpaBand": "GTE_3_5",
  "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다."
}
```

성공 응답 형식은 위와 같고 `data.academicField`는 `"OTHER_UNDECIDED"`다. 기존 계열을 바꾸는 경우에도 선택한 새 코드를 넣어 같은 방식으로 요청한다.

## 5. 마이페이지에서 저장된 계열 조회

### `GET /api/v1/users/me/mypage`

요청 Body와 Query Parameter는 없다.

```http
GET /api/v1/users/me/mypage HTTP/1.1
Accept: application/json
Cookie: access_token=<로그인으로 발급된 쿠키>
```

### 성공 응답 — `200 OK`, 계열 선택 완료

아래는 북마크가 없는 사용자의 전체 응답 예시다. 기존 `summary`와 북마크 목록 구조를 유지하면서 `profile` 안에 `academicField`를 추가한다.

```json
{
  "success": true,
  "data": {
    "profile": {
      "name": "홍길동",
      "nickname": "세부학생",
      "grade": 3,
      "department": {
        "id": "12",
        "name": "컴퓨터공학과"
      },
      "academicField": "ENGINEERING",
      "gpaBand": "GTE_3_5",
      "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다.",
      "profileCompleted": true,
      "profileUpdatedAt": "2026-09-12T14:30:00"
    },
    "summary": {
      "bookmarkedLaboratoryCount": 0,
      "bookmarkedPostCount": 0
    },
    "bookmarkedLaboratories": {
      "items": []
    },
    "bookmarkedPosts": {
      "items": []
    }
  },
  "error": null
}
```

프론트엔드는 `data.profile.academicField`를 읽고 `ENGINEERING`을 **공학계열**로 표시한다. 수정 화면에서도 같은 코드로 선택값을 복원한다.

### 성공 응답 — `200 OK`, 아직 계열을 선택하지 않은 기존 사용자

```json
{
  "success": true,
  "data": {
    "profile": {
      "name": "홍길동",
      "nickname": "세부학생",
      "grade": 3,
      "department": {
        "id": "12",
        "name": "컴퓨터공학과"
      },
      "academicField": null,
      "gpaBand": "GTE_3_5",
      "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다.",
      "profileCompleted": true,
      "profileUpdatedAt": "2026-09-10T11:00:00"
    },
    "summary": {
      "bookmarkedLaboratoryCount": 0,
      "bookmarkedPostCount": 0
    },
    "bookmarkedLaboratories": {
      "items": []
    },
    "bookmarkedPosts": {
      "items": []
    }
  },
  "error": null
}
```

이 경우 조회 화면은 **미선택**, 수정 화면은 **계열을 선택해 주세요**로 표시한다. 새 기능을 지원하는 백엔드는 필드를 생략하지 않고 명시적으로 `null`을 반환한다.

## 6. 저장 정책과 기존 사용자 처리

| 상황 | 처리 |
| --- | --- |
| 새로 계열을 선택하고 저장 | 7개 중 한 코드를 필수로 받음 |
| 계열만 바꿔 저장 | 전체 입력값과 새 계열을 전송. 계열 변경도 `profileUpdatedAt` 갱신 대상으로 포함 |
| 모든 프로필 입력값이 기존과 같음 | 기존 정책대로 `profileUpdatedAt`을 불필요하게 변경하지 않음 |
| 계열 값 오류, 닉네임 오류, 자기소개 검증 실패 | 해당 요청의 프로필 변경 전체를 저장하지 않음 |
| 기존 사용자의 첫 조회 | `academicField: null`. 사용자 선택 전까지 기존 데이터 유지 |
| 사용자가 선택을 해제한 채 저장 | `400 VALIDATION_ERROR`. 기존에 저장된 계열은 유지 |
| 학사정보 재동기화 또는 학년만 수정 | 이미 선택한 계열을 보존 |

**`profileCompleted`는 현재 판정 기준을 유지한다.** 현재 코드는 실명·학사 학과명·학년이 있는지 확인하며, 이번 계열 추가로 기존 사용자의 완료 상태를 일괄 변경하지 않는다. 따라서 `profileCompleted: true`이면서 `academicField: null`인 사용자가 있을 수 있다. 계열 입력 안내는 `academicField === null`로 판단한다.

프로필 저장에서는 계열을 필수로 검증한다. 이 변경은 **기존 PUT 요청에 필수 필드를 추가하므로 요청 호환성이 바뀐다.** 배포 시 기존 프로필 저장 화면도 반드시 새 필드를 보내도록 함께 전환한다. 권장 순서는 nullable DB 컬럼 추가 → 백엔드·프론트 계약을 함께 배포 → 저장·재조회 확인이다. 구형 클라이언트를 동시에 계속 지원해야 한다면 필수 검증을 단계적으로 적용하는 별도 호환 정책이 필요하다.

## 7. 오류 응답

### 계열 누락 또는 `null` — `400 Bad Request`

기존 4개 필드만 보낸 요청 예시:

```json
{
  "nickname": "세부학생",
  "grade": 3,
  "gpaBand": "GTE_3_5",
  "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다."
}
```

오류 응답:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값을 확인해 주세요.",
    "fieldErrors": [
      {
        "field": "academicField",
        "reason": "INVALID_VALUE",
        "message": "계열을 선택해 주세요."
      }
    ],
    "traceId": null
  }
}
```

`academicField`가 `""` 또는 공백만 있는 문자열일 때도 같은 오류로 처리한다.

### 지원하지 않는 계열 — `400 Bad Request`

요청 예시:

```json
{
  "nickname": "세부학생",
  "grade": 3,
  "academicField": "MEDICAL_HEALTH",
  "gpaBand": "GTE_3_5",
  "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다."
}
```

오류 응답:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값을 확인해 주세요.",
    "fieldErrors": [
      {
        "field": "academicField",
        "reason": "INVALID_VALUE",
        "message": "지원하지 않는 계열입니다. 목록에서 다시 선택해 주세요."
      }
    ],
    "traceId": null
  }
}
```

`"공학계열"`, `"engineering"`, `" ENGINEERING "`도 지원하지 않는 값이다. `academicField`가 숫자·배열·객체·boolean이면 같은 `field`와 `reason`으로 거절하고, `message`는 `"계열은 지정된 문자열 코드 하나로 전달해 주세요."`를 사용한다.

**입력 검증 구현:** `academicField` 전용 역직렬화기가 타입과 코드를 검증하며, 공통 예외 처리기가 해당 오류를 계열 입력란의 `fieldErrors`로 반환한다. 숫자를 enum 순번으로 변환하지 않는다. 빈 문자열·공백 문자열은 필수값 검증으로, 지원하지 않는 코드는 목록 오류로 처리한다. JSON 문법 자체가 깨져 필드를 식별할 수 없으면 기존 `VALIDATION_ERROR`와 빈 `fieldErrors`를 유지한다.

### CSRF 또는 출처 검증 실패 — `403 Forbidden`

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "CSRF_TOKEN_INVALID",
    "message": "요청 출처 또는 CSRF 토큰을 확인해주세요.",
    "fieldErrors": [],
    "traceId": null
  }
}
```

보안 검증이 Body 검증보다 먼저 실행될 수 있다. 계열 오류를 테스트할 때도 유효한 인증 쿠키·CSRF·출처를 함께 보내야 한다.

### 관련 오류 코드

| HTTP | `error.code` | 상황 | 화면 처리 |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 계열·학년·닉네임·자기소개 길이·JSON 입력 오류 | `fieldErrors`가 있으면 해당 입력란에 표시. 없으면 폼 전체 오류 표시 |
| 401 | `ACCESS_TOKEN_INVALID` | 인증 쿠키 누락·무효 | 기존 인증 갱신 흐름 적용 |
| 401 | `ACCESS_TOKEN_EXPIRED` | 만료된 인증 토큰 | 기존 인증 갱신 흐름 적용 |
| 403 | `CSRF_TOKEN_INVALID` | CSRF 누락·불일치 또는 허용되지 않은 출처 | CSRF 쿠키·요청 출처 확인 |
| 403 | `FORBIDDEN` | 인증은 되었지만 접근 권한 없음 | 권한 오류 안내 |
| 404 | `USER_NOT_FOUND` | 조회 서비스에서 사용자를 찾지 못함 | 기존 오류 안내 |
| 409 | `NICKNAME_ALREADY_EXISTS` | 닉네임 중복 | 닉네임 입력란에 표시 |
| 409 | `PROFILE_UPDATE_CONFLICT` | 프로필 저장 충돌 | 최신 프로필을 다시 확인하고 재시도 |
| 422 | `CONTENT_POLICY_VIOLATION` | 자기소개 내용 검증 실패 | 자기소개 입력란에 표시. 계열도 저장되지 않음 |
| 429 | `RATE_LIMIT_EXCEEDED` | 요청 제한 초과 | `Retry-After` 헤더 참고 |
| 503 | `CONTENT_MODERATION_UNAVAILABLE` | 자기소개 검증 불가 | 입력을 유지하고 재시도 안내 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류 | 저장 실패 안내 |

공통 오류 형식과 기존 오류 코드는 유지한다. `traceId`는 현재 코드처럼 `null`일 수 있다.

## 8. 프론트엔드 연결 순서

1. 마이페이지 진입 시 `GET /api/v1/users/me/mypage`를 호출한다.
2. `data.profile.academicField`로 선택값을 초기화한다. `null`이면 아무 항목도 자동 선택하지 않는다.
3. 2절의 순서대로 7개 계열을 표시하고 하나만 선택하게 한다.
4. 저장 시 `nickname`, `grade`, `academicField`, `gpaBand`, `introduction`을 함께 PUT 요청한다. 선택한 계열이 없으면 요청 전에 입력 안내를 표시한다.
5. 성공하면 `data`가 저장된 프로필 객체이므로 해당 값으로 화면 상태를 갱신한다. 저장 응답에 `data.profile` 경로는 없다.
6. 새로고침 후 GET 응답의 `data.profile.academicField`에서 같은 코드가 유지되는지 확인한다.
7. 오류이면 입력 중인 값은 유지한다. 인증 재시도는 기존 쿠키 인증 계약을 따르고, 403·429·네트워크 오류를 일괄 로그아웃으로 처리하지 않는다.

## 9. 백엔드 구현 위치

아래 위치에 계열 기능을 구현했다. DB 변경은 배포 시 Flyway V40으로 적용한다.

| 위치 | 구현 내용 |
| --- | --- |
| `user/domain/AcademicField.java` | 신규 enum. 2절의 7개 코드 사용 |
| `user/domain/AppUser.java` | nullable `academicField` 추가. 문자열 enum 저장. 프로필 변경 감지와 저장에 포함 |
| `mypage/dto/ProfileUpdateRequest.java`, `AcademicFieldDeserializer.java` | 필수 `academicField`와 전용 문자열 코드 검증 |
| `mypage/dto/ProfileResponse.java` | 저장 응답의 `academicField` 추가 |
| `mypage/dto/MyPageResponse.java` | 내부 `Profile`의 `academicField` 추가 |
| `mypage/service/ProfileService.java` | 기존 트랜잭션 안에서 계열까지 저장하고 응답에 매핑 |
| `mypage/service/MyPageService.java` | 조회 프로필에 저장된 계열 매핑 |
| `global/exception/ExceptionControllerAdvice.java` 및 입력 검증 처리 | 계열 오류를 `VALIDATION_ERROR` / `academicField` / `INVALID_VALUE`로 매핑 |
| `src/main/resources/db/migration/V40__add_app_user_academic_field.sql` | `app_user.academic_field VARCHAR(32) NULL`과 7개 코드 CHECK 제약 추가. 기존 사용자는 `NULL` 유지 |
| `AppUser` 학사정보 동기화·학년 수정 경로 | 기존 계열 보존. `profileCompleted` 판정은 현재 기준 유지 |
| `AppUser.anonymize()` | 기존 개인정보 익명화 흐름에서 계열도 `null`로 정리 |
| `mypage/controller/MyPageOpenApiSchemas.java`, `MyPageOpenApiExamples.java`, `mypage/config/MyPageOpenApiConfiguration.java` | 요청·응답 예시와 전용 스키마 이름 제공. OpenAPI 3.1에서 미선택 계열의 null을 타입과 enum 양쪽에 반영 |

위 Java 경로는 `src/main/java/com/sebu/backend/` 기준이다. 같은 이름의 빈 클래스 `user/service/ProfileService.java`가 아니라 실제 저장을 담당하는 **`mypage/service/ProfileService.java`**를 수정한다. V40은 nullable 컬럼을 추가하며 기존 행을 일괄 갱신하지 않는다. MySQL에서 ALTER TABLE 실행 시 메타데이터 잠금이 발생할 수 있으므로 배포 시 진행 중인 장기 트랜잭션을 확인한다.

### 검증 기준

- 7개 코드 각각 저장 후 재조회했을 때 같은 코드가 반환된다.
- 계열만 변경해도 저장되며 변경 시각이 갱신된다. 학과·실명은 그대로다.
- 계열이 없는 기존 사용자의 조회는 성공하고 `academicField: null`을 포함한다. 조회만으로 값을 저장하지 않는다.
- 누락·`null`·빈 문자열·공백 문자열은 400이며 계열 입력란을 식별할 수 있다.
- 지원하지 않는 코드·한글·소문자·공백이 붙은 코드·숫자·배열·객체·boolean을 거절하고 숫자가 enum 순번으로 변환되지 않도록 한다.
- 닉네임 충돌이나 자기소개 검증 실패 시 계열을 포함한 변경 전체가 저장되지 않는다.
- 학사정보 재동기화·학년만 수정·재로그인 후에도 선택한 계열이 보존된다.
- 인증이 유효하고 CSRF·출처가 잘못된 저장 요청은 403으로 거절되며 값이 바뀌지 않는다.
- 다른 사용자의 프로필에 영향을 주지 않는다. 본인 정보는 인증 사용자 기준으로만 처리한다.
- 기존 프로필 완료 판정과 북마크 응답 구조가 유지된다. DB 마이그레이션 후 기존 행을 정상 조회할 수 있다.

## 10. 기준 코드

문서의 기존 동작은 다음 파일을 확인해 작성했다.

- [마이페이지 Controller](../src/main/java/com/sebu/backend/mypage/controller/MyPageController.java)
- [현재 프로필 저장 요청 DTO](../src/main/java/com/sebu/backend/mypage/dto/ProfileUpdateRequest.java)
- [현재 프로필 저장 응답 DTO](../src/main/java/com/sebu/backend/mypage/dto/ProfileResponse.java)
- [현재 마이페이지 응답 DTO](../src/main/java/com/sebu/backend/mypage/dto/MyPageResponse.java)
- [프로필 저장 Service](../src/main/java/com/sebu/backend/mypage/service/ProfileService.java)
- [마이페이지 조회 Service](../src/main/java/com/sebu/backend/mypage/service/MyPageService.java)
- [사용자 엔티티와 프로필 완료 판정](../src/main/java/com/sebu/backend/user/domain/AppUser.java)
- [공통 응답 구조](../src/main/java/com/sebu/backend/global/response/ApiResponse.java)
- [공통 예외 처리](../src/main/java/com/sebu/backend/global/exception/ExceptionControllerAdvice.java)
- [쿠키 기반 인증 계약](cookie-authentication.md)

## 11. PR #71과의 요청·응답 계약 비교

**판정: 공통 응답 형식은 유지했다. PR #71에 없는 계열 저장·조회·검증을 이 브랜치에서 추가했다.**

확인 대상은 [PR #71 — 연구실 목록에 연구 분야 ID와 카테고리 연결 정보 제공](https://github.com/greedy-team/SEBU-backend/pull/71)이며, head는 `d933ca2bbe1cfd2407958ca2c8183a3988f8cc55`다. 확인 당시 OPEN 상태이며, 운영 서버에 반영되었다는 의미는 아니다.

PR의 base `2a919e5`는 이 명세 작성에 사용한 로컬 커밋과 같다. 변경 코드와 PR head의 파일 정보를 대조했으며, 마이페이지 Controller·DTO·Service, 공통 응답·예외 처리, 인증, 사용자 엔티티, 북마크 응답, 쿠키 인증 문서 등 관련 파일 13개는 기준 커밋과 내용이 동일하다.

| 비교 항목 | PR #71 기준 실제 계약 | 계열 기능 브랜치의 계약 | 판정 |
| --- | --- | --- | --- |
| 성공 응답 바깥 구조 | `success: true`, `data`, `error: null` | 동일 | 일치 |
| 오류 응답 바깥 구조 | `success: false`, `data: null`, `error` | 동일 | 일치 |
| 프로필 저장 응답 | `data` 자체가 프로필 | `data` 자체가 프로필 | 일치 |
| 마이페이지 조회 응답 | `data.profile`이 프로필 | `data.profile`이 프로필 | 일치 |
| 마이페이지 학과 | `department`, 내부 `id`는 문자열 또는 `null` | 동일 | 일치 |
| 계열 요청·응답 필드 | `academicField` 없음 | 저장 요청·저장 응답·조회 프로필에 추가 | 신규 기능 |
| 프로필 저장 입력 | `nickname`, `grade`, `gpaBand`, `introduction` | 기존 필드 + 필수 `academicField` | 요청 계약 확장. 동시 배포 필요 |
| 잘못된 계열의 상세 오류 | 계열 자체가 검증 대상에 없음 | `fieldErrors`에 `academicField`를 지정 | 신규 검증·오류 매핑 필요 |
| 기존 JSON 역직렬화 오류 | `VALIDATION_ERROR`, `fieldErrors: []` | 계열 오류는 필드별 안내로 확장. 식별 불가 JSON 문법 오류는 기존 방식 유지 | 의도한 확장 |
| 인증·CSRF | Access 쿠키 + 변경 요청 CSRF 검증 | 동일 | 일치 |
| 마이페이지의 북마크 연구실 | 기존 `researchFields` 문자열 배열 | 기존 구조 유지 | 일치 |

### PR에서 추가한 연구 분야 정보와 전공 계열의 차이

PR #71은 `GET /api/v1/laboratories`의 **각 연구실 객체**에 `researchFieldDetails`를 추가한다. 일반 전체 목록과 `sort=REVIEW_COUNT_DESC` 페이지 목록에 모두 적용된다. 기존 `researchFields`를 제거하거나 객체 배열로 바꾸는 변경은 아니다.

| 필드 | 값의 형태 | 표현하는 대상 |
| --- | --- | --- |
| `researchFields` | 문자열 배열 | 연구실에 연결된 연구 분야 이름 |
| `researchFieldDetails` | `researchFieldId`, `name`, `categoryIds`를 가진 객체 배열 | 연구 분야별 ID와 연결된 카테고리 ID |
| `academicField` | `ENGINEERING` 등의 문자열 코드 하나 | 사용자가 마이페이지에서 선택한 전공 계열 |

따라서 이 명세의 `academicField`를 `researchFieldId`나 `categoryIds`로 바꾸지 않는다. PR의 연구 분야 분류와 마이페이지의 전공 계열은 서로 다른 데이터다.

또한 연구실 목록 API의 `researchFieldId`·`categoryIds`는 숫자이고, 마이페이지의 `department.id`는 기존 코드부터 문자열이다. 이 차이는 **API별 기존 DTO 타입 차이**이며, 마이페이지 명세에서 ID를 잘못 표기한 것이 아니다. PR은 마이페이지와 북마크 응답에 `researchFieldDetails`를 추가하지 않는다.

근거:

- [PR head의 공통 응답 DTO](https://github.com/greedy-team/SEBU-backend/blob/d933ca2bbe1cfd2407958ca2c8183a3988f8cc55/src/main/java/com/sebu/backend/global/response/ApiResponse.java)
- [PR head의 프로필 저장 요청 DTO](https://github.com/greedy-team/SEBU-backend/blob/d933ca2bbe1cfd2407958ca2c8183a3988f8cc55/src/main/java/com/sebu/backend/mypage/dto/ProfileUpdateRequest.java)
- [PR head의 프로필 저장 응답 DTO](https://github.com/greedy-team/SEBU-backend/blob/d933ca2bbe1cfd2407958ca2c8183a3988f8cc55/src/main/java/com/sebu/backend/mypage/dto/ProfileResponse.java)
- [PR head의 마이페이지 응답 DTO](https://github.com/greedy-team/SEBU-backend/blob/d933ca2bbe1cfd2407958ca2c8183a3988f8cc55/src/main/java/com/sebu/backend/mypage/dto/MyPageResponse.java)
- [PR head의 연구실 목록 응답 DTO](https://github.com/greedy-team/SEBU-backend/blob/d933ca2bbe1cfd2407958ca2c8183a3988f8cc55/src/main/java/com/sebu/backend/laboratory/dto/LaboratoriesResponse.java)
