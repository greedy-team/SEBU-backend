# 회원 탈퇴 및 계정 복구 계약

## 핵심 정책

- 회원 탈퇴는 `app_user.deleted_at`을 기록하고 `auth_version`을 증가시키며, 해당 사용자의 Refresh Token과 복구 토큰을 같은 트랜잭션에서 즉시 물리 삭제한다.
- 탈퇴 직후부터 공개 작성자 응답은 `{ "id": null, "nickname": null, "status": "WITHDRAW" }`로 마스킹한다.
- 게시글·댓글·연구실 후기와 내부 `author_id`는 유지한다. 학번을 포함한 인증 식별자는 외부 DTO에 포함하지 않는다.
- 개인정보와 좋아요·북마크는 탈퇴 후 30일 동안 복구를 위해 보관한다. 집계 쿼리는 `user.deleted_at IS NULL` 조건으로 탈퇴 사용자의 반응을 즉시 제외한다.
- 30일 안에 명시적으로 복구하면 기존 사용자 ID, 프로필, 작성물, 좋아요와 북마크가 다시 활성화된다.
- 30일이 지나면 개인정보와 인증 식별자를 익명화하고 좋아요·북마크를 물리 삭제한다. 이후 같은 학번으로 로그인하면 신규 계정을 생성하며 기존 작성물의 소유권은 이어받지 않는다.

외부 사용자 상태는 `deleted_at == null`이면 `ACTIVE`, 아니면 `WITHDRAW`다. `anonymized_at`은 30일 경과 후 처리 완료 여부만 나타내는 내부 필드다.

## 복구 가능 시간

복구 대기시간은 `minimumRecoveryCooldown`으로 정하며 기본값은 1시간이다. 대기시간 이후부터 `deleted_at + 30일` 미만까지 복구할 수 있고, `deleted_at + 30일` 이상이면 만료다.

Access Token에는 발급 당시의 `authVersion`을 넣고 보호 API마다 `app_user.auth_version`과 비교한다. 탈퇴 시 DB 값을 증가시키고 복구할 때 되돌리지 않으므로 탈퇴 전에 발급된 Access Token은 복구 후에도 다시 유효해지지 않는다. 따라서 복구 대기시간은 Access Token 만료시간에 의존하는 보안 장치가 아니라 탈퇴 직후 실수와 반복 요청을 줄이기 위한 정책이다.

## API

모든 변경 요청에는 `XSRF-TOKEN` 쿠키와 같은 값의 `X-XSRF-TOKEN` 헤더가 필요하다. Access, Refresh, Recovery 원문은 JSON으로 반환하지 않는다.

### 회원 탈퇴

```http
DELETE /api/v1/users/me
Cookie: access_token=...
X-XSRF-TOKEN: ...
```

성공하면 `204 No Content`를 반환하고 `access_token`, `refresh_token`, `recovery_token` 쿠키를 삭제한다. 서버에서는 사용자 행을 잠근 뒤 `deleted_at` 기록, `auth_version` 증가, Refresh/Recovery Token 행 삭제를 하나의 트랜잭션으로 처리한다.

### 세종대학교 로그인

```http
POST /api/v1/auth/sejong/login
Content-Type: application/json

{
  "studentId": "22000000",
  "password": "********"
}
```

활성 사용자와 신규 사용자는 로그인 쿠키를 발급한다.

```json
{
  "success": true,
  "data": {
    "loginStatus": "AUTHENTICATED",
    "expiresIn": 1800,
    "user": {
      "id": 12,
      "isNewUser": false,
      "profileCompleted": true
    }
  },
  "error": null
}
```

복구 대기시간 안에 다시 로그인하면 `409 ACCOUNT_RECOVERY_COOLDOWN`을 반환하며 인증 쿠키를 발급하지 않는다.

복구 대기시간이 지났고 30일이 지나지 않았다면 학교 인증 성공 후에도 자동 복구하지 않는다. Access/Refresh 쿠키를 삭제하고, 최대 5분인 일회용 `recovery_token` HttpOnly 쿠키만 발급한다.

```json
{
  "success": true,
  "data": {
    "loginStatus": "RECOVERY_REQUIRED",
    "recoveryExpiresIn": 300,
    "recoverableUntil": "2026-10-03T06:00:00Z"
  },
  "error": null
}
```

응답에는 사용자 ID, 학번, 이름, 닉네임, 프로필을 포함하지 않는다. 다시 로그인해 새 복구 토큰을 발급하면 이전 복구 토큰은 즉시 무효화된다.

30일 이상 경과했는데 스케줄러 처리가 늦었다면 로그인 트랜잭션에서 기존 계정을 먼저 익명화한 후 신규 계정을 생성한다.

### 계정 복구

```http
POST /api/v1/auth/recovery
Cookie: recovery_token=...
X-XSRF-TOKEN: ...
```

요청 Body는 없다. `recovery_token` 쿠키의 Path는 `/api/v1/auth/recovery`이며 `HttpOnly`, `Secure`, `SameSite=Lax`를 사용한다. 토큰 원문은 로그나 DB에 저장하지 않고 DB에는 SHA-256 해시만 저장한다.

성공하면 `deleted_at`을 `null`로 되돌리고 새 Access/Refresh 쿠키를 발급하며 복구 토큰을 물리 삭제한다. 응답은 `loginStatus: AUTHENTICATED`, `isNewUser: false`인 로그인 성공 형태다. 동일 복구 토큰의 동시 요청은 사용자 행과 토큰 행 잠금으로 한 건만 성공한다.

만료·사용·교체·삭제된 토큰은 모두 `401 RECOVERY_TOKEN_INVALID`이며, 응답에서 Recovery 쿠키만 삭제한다. 30일 경과 여부를 판별하기 위해 삭제된 개인정보를 조회하는 `410` 응답은 사용하지 않는다.

## 작성자 응답

```json
{
  "id": 12,
  "nickname": "세부",
  "status": "ACTIVE"
}
```

```json
{
  "id": null,
  "nickname": null,
  "status": "WITHDRAW"
}
```

FE는 작성자 상태를 `id` 비교로 추론하지 않고 `status`로 판단한다. 특히 `null === null` 비교로 서로 다른 탈퇴 작성자를 현재 사용자로 판정하면 안 된다. 프로필 이동과 작성자 상세 조회도 `ACTIVE`일 때만 허용한다.

## 30일 경과 처리

만료 복구 토큰 삭제와 계정 익명화는 한 작업의 실패가 다른 작업을 막지 않도록 분리한다. 한국시간 매일 03:15에 만료 복구 토큰을 삭제하고, 03:20에 UTC 기준 시각으로 다음 조건의 계정을 배치 처리한다.

```text
deleted_at <= now - 30일 AND anonymized_at IS NULL
```

계정마다 사용자 행을 잠근 뒤 상태와 기한을 다시 확인한다. `app_user.id`, `deleted_at`, 게시글·댓글·연구실 후기와 그 `author_id`는 유지한다. 다음 데이터는 제거한다.

- `provider`, `provider_user_id`, `email`, 이름·닉네임·학년·학과·GPA·자기소개와 관련 검수 정보
- `refresh_token`, `account_recovery_token`
- 연구실 북마크, 게시글 북마크, 게시글 좋아요

설정 키는 `app.auth.account.recovery-window`, `minimum-recovery-cooldown`, `recovery-token-expiration`, `recovery-token-cleanup-cron`, `anonymization-cron`, `batch-size`, `max-batches`다. 복구 토큰 수명은 설정 오류가 있어도 5분을 넘길 수 없다. 한 번 적용된 Flyway 파일은 수정하지 않고 후속 스키마 변경은 새 버전 마이그레이션으로 추가한다.
