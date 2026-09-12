# SEBU Backend

학부 연구실 검색 서비스의 백엔드 애플리케이션입니다.

## 기술 스택

- Java 21
- Spring Boot 3.5.3
- Gradle 8.14.3 (Wrapper)
- Spring Web
- Spring Data JPA
- Bean Validation
- H2 (로컬 개발)
- MySQL 8.0.19 이상 (배포)

## 패키지 구조

비즈니스 기능을 먼저 찾을 수 있도록 feature-first 구조를 사용합니다. 각 기능 내부는 MVC 역할에 따라 분리합니다.

```text
com.sebu.backend
├─ laboratory
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ domain
│  ├─ dto
│  ├─ config
│  └─ scheduler
├─ bookmark
│  ├─ service
│  ├─ repository
│  └─ domain
├─ crawling
│  ├─ service
│  ├─ repository
│  ├─ domain
│  ├─ dto
│  ├─ port
│  ├─ adapter
│  ├─ config
│  └─ runner
├─ college | department | professor | researchfield | user
│  ├─ repository
│  └─ domain
└─ global
   ├─ auth
   ├─ response
   ├─ domain
   └─ ratelimit
```

HTTP 요청은 `controller`가 받고, 비즈니스 흐름은 `service`, 데이터 접근은 `repository`, 엔티티와 값 객체는 `domain`, API 및 서비스 전달 객체는 `dto`가 담당합니다. 크롤러는 HTTP 진입점이 없는 배치 기능이므로 `controller` 대신 `runner`와 `port`/`adapter` 경계를 유지합니다.

## 실행

Windows에서는 저장소 루트에서 다음 명령을 실행합니다.

```powershell
.\gradlew.bat bootRun
```

기본 프로필은 `local`이며 H2 인메모리 DB를 사용합니다.

- H2 Console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:sebu`
- Username: `sa`
- Password: 없음

### API 문서

로컬 환경에서 애플리케이션을 실행한 후 Swagger UI에서 API 명세를 확인하고 직접 요청할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

인증이 필요한 API는 먼저 `GET /api/v1/auth/csrf`를 호출한 뒤 세종대학교 로그인을 실행합니다. 인증은 브라우저가 저장한 HttpOnly 쿠키를 사용하며, 변경 요청에는 현재 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 전달합니다. 자세한 흐름은 [쿠키 인증 계약](docs/cookie-authentication.md)을 참고합니다. 운영 환경에서는 Swagger UI와 OpenAPI 문서를 노출하지 않습니다.

### 세종 포털 로그인 TLS 호환성

세종 포털과 학사정보시스템은 현재 Java 21 기본 보안 정책이 차단하는 구형
`TLSv1.2 / TLS_RSA_WITH_AES_256_CBC_SHA` 조합만 협상합니다. 백엔드는 JVM 전역 보안
설정을 낮추지 않고, 세종 인증용 HTTP 클라이언트에만 Conscrypt를 적용해 이 조합을
허용합니다. 표준 인증서 체인 검증과 HTTPS 호스트명 검증은 그대로 유지됩니다.

로컬 `bootRun`에는 별도 TLS 설정이 필요하지 않습니다. Docker 이미지는 Conscrypt의
네이티브 라이브러리와 호환되는 Ubuntu Jammy 기반 Temurin 이미지를 사용합니다.
세종 측 TLS가 개선되면 기본 Java TLS로 자동 재시도하며, 장기적으로는 이 호환 계층을
제거하는 것이 권장됩니다.

## Docker 실행

Docker Compose로 Java 21 빌드와 백엔드 실행을 한 번에 처리할 수 있습니다.

인증 기능을 초기화하려면 32바이트 이상의 랜덤 키를 Base64로 인코딩한
`JWT_SECRET_BASE64` 환경 변수가 필요합니다. PowerShell에서는 다음과 같이 생성합니다.

```powershell
$secretBytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($secretBytes)
$random.Dispose()
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($secretBytes)
```

환경 변수 대신 `.env.example`을 `.env`로 복사한 다음 `JWT_SECRET_BASE64` 값을 채워도 됩니다.
`.env` 파일은 Git에서 제외되며 비밀키를 저장소에 커밋하지 않습니다.

```powershell
docker compose up --build -d
docker compose ps
```

기본적으로 `local` 프로필과 H2 인메모리 DB를 사용하며, 다음 API로 응답을 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/laboratories
```

호스트의 8080 포트가 사용 중이면 `BACKEND_PORT`로 변경할 수 있습니다.

```powershell
$env:BACKEND_PORT = 18080
docker compose up --build -d
Invoke-RestMethod http://localhost:18080/api/v1/laboratories
```

로그 확인, 재시작 및 종료 명령은 다음과 같습니다.

```powershell
docker compose logs -f backend
docker compose restart backend
docker compose down
```

## 테스트

```powershell
.\gradlew.bat test
```

## 교수 정보 크롤링

교수 정보 크롤러는 일반 서비스 실행과 분리된 일회성 작업입니다. MySQL 연결, 단일 출처 시험 실행, 전체 출처 실행 및 결과 확인 방법은 [교수 정보 1차 크롤링 실행 안내](docs/professor-crawling.md)를 참고합니다.

## 연구실 삭제 보존 정책

- 연구실 삭제 시 `deleted_at`을 기록하여 30일간 복구할 수 있는 상태로 보존합니다.
- 매일 새벽 3시에 30일이 지난 연구실을 물리 삭제합니다.
- 물리 삭제 시 연구 분야 매핑과 북마크는 DB `ON DELETE CASCADE`에 따라 함께 삭제됩니다.
- 보존 기간과 실행 시간은 `app.laboratory-retention` 설정으로 변경할 수 있습니다.

## 배포 프로필

`prod` 프로필은 MySQL 접속 정보를 환경 변수로 받습니다.

```text
DB_URL=jdbc:mysql://host:3306/database
DB_USERNAME=...
DB_PASSWORD=...
JWT_SECRET_BASE64=... # Base64로 인코딩한 32바이트 이상의 랜덤 키
SPRING_PROFILES_ACTIVE=prod
```

JWT Access Token 서명 키는 코드나 설정 파일에 저장하지 않고 `JWT_SECRET_BASE64` 환경 변수로 전달합니다.
로컬 실행에서도 같은 환경 변수가 필요하며, 테스트는 `src/test/resources/application.yml`의 테스트 전용 키를 사용합니다.

운영 환경에서는 TLS를 종료하는 리버스 프록시만 외부에 공개하고 백엔드 8080 포트는 프록시에서만 접근할 수 있게 제한합니다. `prod` 프로필은 `X-Forwarded-Proto`를 기준으로 원래 요청이 HTTP이면 HTTPS로 전환하고, `X-Forwarded-For`를 반영한 클라이언트 IP를 로그인 요청 제한에 사용합니다.

프록시는 클라이언트가 보낸 `X-Forwarded-For`와 `X-Forwarded-Proto`를 그대로 전달하지 말고 반드시 덮어써야 합니다. 이 조건과 백엔드 직접 접근 차단이 지켜지지 않으면 전달 헤더를 위조해 IP 제한을 우회할 수 있습니다.
