# 운영·보안 핵심 로깅 — 1차 구현

API 서버는 핵심 사건을 한 줄 JSON으로 표준 출력에 남긴다. 별도 DB 쓰기, 수집 에이전트,
외부 로그 API 호출, 대시보드 서버는 추가하지 않는다. **이번 PR은 코드와 로컬 검증까지다.**
AWS 수집·14일 보존·접근 권한 설정은 계정 담당자와 개발 서버에서 별도로 확인해야 한다.

## 기록하는 내용

| 사건 | 기록 내용 | 수준 |
|---|---|---|
| `http.request.completed` | 서버 생성 traceId, HTTP 메서드, 매핑된 경로 템플릿, 상태, 소요 시간 | INFO |
| `auth.login.completed` | 성공/거부/실패/복구 확인 필요, 고정 사유, 성공 시 신규 여부, 확인 가능한 SSO 단계 | INFO / WARN / ERROR |
| `auth.refresh.completed` | 회전 성공 또는 형식 오류·미존재·사용자 비활성·폐기·만료 사유 | INFO |
| `auth.logout.completed` | 요청 처리 완료. 토큰이 실제 폐기됐다는 의미는 아님 | INFO |
| `auth.recovery.completed` | 복구 성공 또는 복구 토큰 거부 | INFO |
| `security.access.rejected` | 인증·권한·CSRF·요청 출처 거부 사유 | INFO |
| `security.rate_limit.rejected` | 제한 분류, 경로 템플릿, 재시도 대기 초 | INFO |
| `auth.department.unresolved` | 학과 조회 일치 건수만 | WARN |
| `external.request.failed` | 콘텐츠 검증 연동 최종 실패, 정제된 예외 정보 | ERROR |
| `api.unexpected_failure` | 예상하지 못한 최종 API 실패, 고정 오류 코드와 정제된 예외 정보 | ERROR |
| `batch.completed` / `batch.failed` | 작업명, 실행마다 새 runId, 처리 수, 알려진 실패 수, 소요 시간, 처리 상한 도달 여부 | INFO / ERROR |
| `framework.diagnostic` | 라이브러리 분류와 예외 종류. 자유 형식 원문은 생략 | 기본 WARN 이상 |
| `logging.events.dropped` | 표준 출력 큐 초과로 버린 로그 건수 | WARN |

- 로그인 식별 불일치와 허용되지 않은 SSO 리다이렉트는 WARN이다. 비밀번호 오류나 429 한 건을 공격으로 판단하지 않는다.
- 복구 확인이 필요한 HTTP 200은 `RECOVERY_REQUIRED`이며 로그인 성공으로 세지 않는다.
- SSO 클라이언트는 안전한 단계/사유를 예외에 전달하고, 최종 인증 예외 처리기에서 한 번 기록한다.
- 일반 요청 요약과 해당 요청의 최종 실패 상세는 별도 사건이다. 같은 예외 원문을 계층마다 반복 출력하지 않는다.
- API 요청은 정상 요청도 한 건씩 기록한다. `/api/` 밖의 정상 요청(메트릭 조회 등)은 요약에서 제외한다.
- 현재 Docker 상태 확인은 `/api/v1/laboratories`를 호출하므로 일반 API 요약에 포함된다.
  임의 헤더로 상태 확인을 주장해도 로깅을 우회시키지 않는다. 전용 상태 확인 경로 전환은 배포 담당자와 후속 검토한다.
- 보안 필터에서 먼저 거부되거나 경로가 매핑되지 않으면 `route=UNMATCHED`다. 원본 URI를 대신 쓰지 않는다.

## 추적 ID와 개인정보

클라이언트의 `X-Request-ID`는 신뢰하지 않는다. 요청마다 새 난수 ID를 만들어 응답의
`X-Request-ID`, 오류 응답의 `error.traceId`, 해당 요청 로그의 `traceId`에 동일하게 사용한다.
정상 응답 본문 구조는 그대로다. 요청이 끝나거나 예외가 발생하면 MDC의 ID를 제거한다.
사용자를 추적하는 ID나 분산 추적 시스템은 아니다.

기록하지 않는 값:

- 학번·이름·이메일·닉네임·사용자 ID·IP·프로필 원문
- 비밀번호·토큰·토큰 해시·쿠키·세션 ID·API 키
- 요청/응답 본문, 쿼리 문자열, HTTP 헤더, DTO/엔티티 전체
- 예외 메시지, cause 메시지, suppressed 내용, 임의 MDC, 자유 형식 로그 메시지

`OperationalLog`의 사건별 메서드를 사용한다. 문자열 사유/작업명은 **개발자가 정한 상수나 enum**만 전달한다.
형식 검사는 개인정보 판별기가 아니므로 외부 값을 넣고 안전하다고 판단하면 안 된다.
예외는 종류와 코드 위치만 최대 원인 4개 × 위치 8개까지 출력하고 생략 여부를 표시한다.
배치의 여러 실패 중 첫 번째 예외만 상세로 남기고 나머지는 건수로 요약한다.

로컬과 서버 모두 같은 JSON 출력을 적용한다. 로컬에서도 실제 계정으로 테스트할 수 있기 때문이다.
기존 `log.info("자유 형식 ...")`는 기본 수준상 출력되지 않으며 WARN/ERROR 원문도 정제 출력으로 대체된다.
라이브러리 오류 메시지가 없어 진단 정보가 줄어드는 의도적인 절충이다. 필요한 정보는 검토한 고정 코드로 추가한다.
SQL `show-sql`, HTTP wire logging, `System.out`/`printStackTrace`, 별도 appender를 추가하면
이 경로를 우회할 수 있으므로 활성화하지 않는다. 로그 수준 DEBUG 변경만으로 원문은 출력되지 않는다.

## 처리 비용과 출력 장애

- 요청 스레드에서는 난수 ID 생성, 시간 측정, 작은 JSON 직렬화와 메모리 큐 삽입만 수행한다.
- 큐에는 원본 요청이나 예외 객체 대신 **정제 완료된 문자열 최대 512건**만 보관한다.
- 출력 전용 데몬 스레드 하나가 stdout에 쓴다. 출력이 막히면 큐 삽입은 기다리지 않고 해당 사건을 버린다.
- 출력이 다시 진행되면 `logging.events.dropped`에 유실 건수를 기록한다. ERROR/보안 로그도 큐 초과 시 유실될 수 있다.
- 종료 시 큐를 최대 2초 동안 비우도록 기다린다. 강제 종료·장기 출력 장애·프로세스 종료 시 미출력 로그와 유실 계수는 사라질 수 있다.
- stdout 이후 Docker/CloudWatch 구간의 유실과 장애는 애플리케이션이 확인하지 못한다.
  이 사건은 **로컬 출력 큐의 유실 표시**이며 수집 장애 탐지나 전달 보장이 아니다.
- CPU·메모리·지연의 실제 증가량은 아직 측정하지 않았다. 실서버에서 같은 트래픽의 적용 전후를 비교해야 한다.

현재 서비스의 동기 Servlet 요청을 대상으로 검증했다. ERROR 재디스패치는 추가 요약을 기록하지 않는다.
필터 밖으로 예외가 나가면 500으로 요약한다. 향후 커스텀 오류 디스패치가 상태를 바꾸거나
`Callable`, `DeferredResult`, SSE 등 비동기 응답을 도입할 때에는 실제 완료 시점과 MDC 전파를 추가 구현·검증해야 한다.

## 배치 건수 해석

| 작업 | processedCount |
|---|---|
| 교수 수집 | 성공한 수집 소스 수 |
| 연구 분야 추출 | 성공한 연구실 수 |
| 교수/연구 분야 승격 | 실패하지 않은 후보 수(건너뜀 포함) |
| 수동 분리 가져오기 | 생성 또는 기존과 동일하게 처리된 행 수 |
| 토큰 정리·계정 익명화·연구실 정리 | 서비스가 반환한 처리 레코드 수 |

수동 분리의 `failedCount`는 거부된 **소스 수**다. 행 수와 합산하지 않는다.
중도 예외로 실패 대상 수를 알 수 없으면 `failedCount`를 생략하며, 이전에 완료한 처리 수만 남긴다.
`limitReached=true`는 이번 실행의 반복 상한에 도달했다는 의미다. 실제 잔여 레코드가 있다는 확정 판정은 아니다.

## 로컬 검증

JDK 21을 사용한다. 아래 첫 명령은 Docker가 필요 없는 로깅·배치·SSO 테스트다.
SSO 테스트는 로컬 가짜 HTTP 서버를 사용하고 실제 학교 계정으로 접속하지 않는다.

```powershell
.\gradlew.bat test --tests '*global.logging.*' --tests '*RunnerTest' --tests '*AccountLifecycleSchedulerTest' --tests '*SejongSsoClientTest' -PexcludeDocker --max-workers=1 --console=plain
```

인증 공통 경로 회귀 테스트도 H2와 가짜 외부 인증으로 실행한다.

```powershell
.\gradlew.bat test --tests '*AuthApiIntegrationTest' --tests '*CookieCsrfIntegrationTest' --tests '*JwtSecurityIntegrationTest' --tests '*AuthSessionServiceIntegrationTest' --tests '*SecurityPublicApiIntegrationTest' -PexcludeDocker --max-workers=1 --console=plain
```

결과는 `build/reports/tests/test/index.html`에서 실패/오류/건너뜀 여부를 확인한다.
오래 걸리는 전체 테스트와 MySQL·컨테이너 검증은 사용자가 Docker를 준비한 뒤 직접 실행한다.

```powershell
.\gradlew.bat test --max-workers=1 --console=plain
```

## 팀 리뷰 후 개발 서버에서 확인할 것

1. 기존 Docker 로그 드라이버와 배포 설정을 확인하고 독립 저장소로 수집한다. AWS 계정 소유자가 설정한다.
2. 원래 정책의 14일 보존을 저장소·로컬 버퍼·복사본에 어떻게 적용할지 결정한다.
   Docker 크기 제한/회전만으로 14일 보존이 구현됐다고 보지 않는다.
3. 앱 쓰기 권한, 팀원 조회 권한, 보존 설정 변경 권한을 나누고 전송/저장 보호와 접근 감사 범위를 확인한다.
4. 가짜 데이터로 수집 중단·재개·컨테이너 재생성 후 로그 조회와 디스크 증가량을 확인한다.
5. 개발 서버의 로그량·응답 지연을 짧게 비교한 뒤 큐 크기, 수집 버퍼, 보존 용량을 조정한다.

대시보드, 자동 알림, 공격 탐지, 자동 차단, 로그 무손실 보장, AWS 권한/보존 설정은 이번 PR에 포함하지 않는다.
