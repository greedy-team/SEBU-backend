# 쿠키 기반 인증 계약

## 변경 범위

학교 Portal → SSO → UserInfo 인증과 JWT 검증은 유지한다. 공식 OAuth나 서버 세션/BFF로 전환한 것이 아니다.
Access/Refresh 원문은 JSON 응답과 프론트 저장소에 넣지 않고 HttpOnly 쿠키로 전달한다.
학교 비밀번호는 기존 로그인 요청에서만 사용하며 DB·쿠키·JWT에 저장하지 않는다.
학번은 8자리 숫자, 비밀번호 길이는 기존 API 계약인 8~64자로 요청 DTO와 서비스를 통일했다. 학교의 비밀번호 최대 길이를 새로 확정한 것은 아니다.

| 쿠키 | HttpOnly | Path | 수명 |
| --- | --- | --- | --- |
| `access_token` | true | `/api/v1` | 기본 30분, 로그인 절대 만료 이내 |
| `refresh_token` | true | `/api/v1/auth` | 마지막 로그인/갱신부터 14일, 로그인 절대 만료 이내 |
| `recovery_token` | true | `/api/v1/auth/recovery` | 최대 5분, 계정 복구 기한 이내 |
| `XSRF-TOKEN` | false | `/` | 세션 쿠키, 로그인·복구·로그아웃·탈퇴 때 교체 |

인증 관련 쿠키는 모두 `SameSite=Lax`, Domain 미지정이며 HTTPS 서버에서는 Secure를 사용한다. local 프로필만 HTTP 테스트를 위해 Secure=false다.
`session_id`는 로그인 묶음 식별자일 뿐 인증 자격증명이 아니며, 클라이언트에 전달하지 않는다.

## API

| 요청 | 입력 | 성공 응답 |
| --- | --- | --- |
| `GET /api/v1/auth/csrf` | 인증 불필요 | 204, 필요한 경우 XSRF-TOKEN 쿠키 발급 |
| `POST /api/v1/auth/sejong/login` | 기존 학번·비밀번호 JSON + CSRF | 200 로그인 완료 또는 복구 확인 필요, 대기 중이면 409 |
| `POST /api/v1/auth/recovery` | Recovery 쿠키 + CSRF, 바디 불필요 | 200, 복구 후 두 인증 쿠키와 새 CSRF 쿠키 |
| `POST /api/v1/auth/refresh` | Refresh 쿠키 + CSRF, 바디 불필요 | 200, 두 인증 쿠키 갱신 |
| `POST /api/v1/auth/logout` | Refresh 쿠키 + CSRF, 바디 불필요 | 200, 현재 로그인 묶음 폐기 및 인증·복구 쿠키 삭제 |
| `GET /api/v1/me` | Access 쿠키 | 기존 내 정보 응답, 토큰 발급 없음 |
| `DELETE /api/v1/users/me` | Access 쿠키 + CSRF | 204, 탈퇴·모든 Refresh 행 물리 삭제·인증/복구 쿠키 삭제 |

로그인 응답의 data는 다음과 같다. `accessToken`, `refreshToken`, `tokenType`은 반환하지 않는다.

```json
{
  "loginStatus": "AUTHENTICATED",
  "expiresIn": 1800,
  "user": { "id": 1, "isNewUser": false, "profileCompleted": false }
}
```

Refresh 응답 data는 `{ "expiresIn": 1800 }`이다. 절대 만료 직전에는 실제 남은 시간만 반환한다.
공통 `success/data/error` 형태와 기존 프로필 완료 판정·학과 매핑은 변경하지 않았다.
로그아웃은 기존 `data.message`를 유지한다. 인증 응답은 `Cache-Control: no-store`다.

## CSRF 및 배포 출처

1. 로그인 전 `/auth/csrf`를 호출한다. 응답 바디는 없으며 쿠키를 읽는다.
2. 모든 POST/PUT/PATCH/DELETE에서 현재 `XSRF-TOKEN` 값을 `X-XSRF-TOKEN` 헤더로 전송한다.
3. 로그인·복구·로그아웃·탈퇴 뒤에는 새 쿠키 값을 읽는다. 이전 값을 메모리에 고정하지 않는다.
4. Origin을 허용 목록과 정확히 비교한다. Origin이 없으면 Referer의 출처를 확인하고, 둘 다 없거나 `Origin: null`이면 거부한다.
5. Spring 리소스 서버의 Bearer 요청 CSRF 자동 예외를 제거했다. **Access 쿠키가 있는 요청도 CSRF가 필수**다. GET/HEAD/OPTIONS/TRACE는 상태 변경을 하지 않아야 한다.

기본 허용 출처는 `https://sebu-frontend.vercel.app`이다. local 프로필은 `http://localhost:5173`, `http://localhost:8080`을 허용한다.
추가 배포 주소는 `app.auth.csrf.allowed-origins`로 명시한다. Vercel 전체 와일드카드를 허용하지 않는다.
프론트의 동일 출처 `/api` 프록시 구조를 전제로 한다. 브라우저에서 API 호스트로 직접 cross-origin 호출하는 구조로 바꾸면 별도로 CORS·쿠키 정책을 검토해야 한다.
실제 프록시가 여러 Set-Cookie 헤더를 각각 보존하는지도 배포 후 확인해야 한다.
PowerShell 등 직접 호출에서도 쿠키 저장소, 허용된 Origin, CSRF 헤더를 함께 보내야 한다. Authorization 헤더만으로는 인증되지 않는다.
Swagger는 `/auth/csrf`를 먼저 실행하고 로그인한다. 토큰을 Bearer 입력란에 붙여 넣는 방식은 더 이상 사용하지 않는다.

## 새로고침 및 오류 처리: 프론트 참고 계약

- 시작 시 `/me`부터 요청한다. Access가 유효하면 Refresh를 호출하지 않는다.
- 인증 401일 때에만 갱신을 한 번 시도하고 원래 요청을 한 번 재시도한다. `/auth/refresh` 자체를 다시 자동 갱신하는 순환은 금지한다.
- 만료된 쿠키는 브라우저에서 제거될 수 있으므로 `ACCESS_TOKEN_EXPIRED`뿐 아니라 인증 쿠키 누락도 고려한다.
- 공개 GET이라도 잘못된/만료된 Access 쿠키가 전송되면 JWT 필터가 401을 반환할 수 있다. 공통 요청 처리에서 복구하되 공개 API의 선택적 사용자 정보 기능은 유지한다.
- 403 `CSRF_TOKEN_INVALID`는 CSRF/출처 오류이며 인증 만료가 아니다. 일반 권한 오류는 `FORBIDDEN`이다.
- 401 `REFRESH_TOKEN_INVALID`에는 사용 완료·만료·폐기·탈퇴·존재하지 않는 토큰이 포함된다. 이 실패 응답은 인증 쿠키를 삭제하지 않는다.
- 중복 요청의 늦은 실패가 다른 요청의 성공 상태를 덮어쓰지 않게 하고, 탭 간 갱신과 로그아웃을 조정한다. 429/502/네트워크 오류를 무조건 로그아웃으로 바꾸지 않는다.
- 프론트 코드 변경은 이 작업에 포함하지 않았다. Axios 외 별도 fetch 경로도 같은 계약이 필요하다.

## 만료·폐기·정리

최초 로그인 시 절대 만료를 30일 뒤로 고정한다. 갱신 시 `min(현재 + 14일, 절대 만료)`로 Refresh 만료를 정하며, Access도 절대 만료를 넘지 않는다.
미사용 시간은 모든 페이지 클릭이 아니라 **로그인/Refresh 성공 시각** 기준이다.
갱신마다 토큰 원문은 바꾸며 DB에는 SHA-256 해시만 저장한다. 사용한 토큰을 다시 보내면 거절하지만, 그 이유만으로 후속 토큰을 자동 폐기하지 않는다.
명시적 로그아웃은 해당 `session_id`의 토큰을 모두 폐기한다. 갱신 전 토큰으로 로그아웃해도 후속 토큰이 폐기된다. 다른 기기의 독립 로그인은 유지한다. 회원 탈퇴는 사용자의 Refresh Token 행을 모두 즉시 물리 삭제한다.
기존 사용자 행 → Refresh 행 순서로 잠그므로 갱신·로그아웃·탈퇴가 직렬화된다. Access JWT의 `authVersion`은 보호 API에서 사용자 행의 `auth_version`과 비교하며, 탈퇴 시 증가하고 복구 시 되돌리지 않는다. 따라서 탈퇴 전에 발급된 Access는 복구 후에도 사용할 수 없다.
로그아웃에서는 아직 폐기되지 않은 현재 로그인 묶음만 조회한다. 탈퇴에서는 이력을 포함한 해당 사용자의 Refresh 행 전체를 벌크 삭제한다.
Refresh 확인 뒤 JWT 발급 사이에 절대 만료를 지나도 401 `REFRESH_TOKEN_INVALID`를 반환하고 해당 갱신 트랜잭션은 롤백한다.

기존 토큰 이력은 로그아웃의 묶음 식별을 위해 절대 만료까지 보존한다. 미사용으로 만료됐다고 즉시 삭제하지는 않는다.
정리 작업은 한국시간 매일 03:10에 절대 만료된 행을 500개씩, 최대 100배치 삭제한다. 각 배치는 별도 트랜잭션이며 사용자/프로필/게시물/북마크를 삭제하지 않는다.
`app.auth.retention.purge-cron`(`-`이면 중지), `batch-size`, `max-batches`로 조절한다. 처리량 한도를 넘는 잔여 데이터는 다음 실행에서 정리한다.

## 마이그레이션 및 배포

`V37`은 기존 refresh_token에 session_id와 absolute_expires_at을 추가한다. `V38`은 `app_user.anonymized_at`과 해시 기반 `account_recovery_token` 테이블을 추가하고, `V39`는 `app_user.auth_version`을 추가한다.
구형 토큰은 최초 로그인 시각을 복원할 수 없으므로 기존 행은 남기되 폐기 상태로 전환한다. 기존 사용자 데이터는 보존한다.
프론트·백엔드를 함께 전환하며 기존 사용자는 한 번 다시 로그인해야 한다. 구형 Bearer 방식은 병행 지원하지 않는다.
이미 적용한 V14~V36을 수정하거나 V37의 체크섬을 다시 바꾸어 배포하지 않는다. 운영 전진 수정은 새 버전으로 한다.

## 의도적으로 남긴 한계

- 재사용 감지에 따른 로그인 묶음 자동 폐기, 시간 기반 유예, 갱신 응답 유실 자동 복구는 후속 작업이다.
- 공격자가 먼저 갱신해 받은 후속 Refresh는 재사용 탐지만으로 자동 차단하지 못한다.
- 갱신 응답을 받지 못하면 재로그인이 필요할 수 있다.
- 로그아웃 후 외부에 복사된 Access JWT는 남은 수명 동안 유효할 수 있다. JWT 차단 목록/요청별 로그인 상태 조회는 추가하지 않았다.
- HttpOnly는 JavaScript의 토큰 읽기를 제한하지만 XSS에 의한 요청 대행까지 막지는 못한다.

회원 탈퇴·복구의 시간 경계, 작성자 마스킹, 데이터 보존 정책은 [회원 탈퇴 및 계정 복구 계약](account-withdrawal-recovery.md)을 참고한다.

## 테스트 명령 (Windows, JDK 21)

```powershell
.\gradlew.bat test -PexcludeDocker --no-daemon --max-workers=1
.\gradlew.bat test --tests '*MySql*' --tests '*BookmarkConcurrencyIntegrationTest' --no-daemon --max-workers=1
```

두 번째 명령은 Docker 엔진이 필요하다. MySQL 8.4에서 기존 데이터 업그레이드, 제약조건, 최초 로그인·갱신·로그아웃·탈퇴 경합을 검증한다.
Testcontainers 테스트가 skipped라면 MySQL 검증을 완료한 것으로 보지 않는다.
