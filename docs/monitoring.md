# 최소 부하 모니터링

EC2에는 Actuator와 Micrometer만 추가한다. Prometheus, Grafana, Alloy, Loki 서버를 EC2에 설치하지 않는다.
서버 CPU·상태 검사·네트워크·T 계열 CPU 크레딧은 AWS CloudWatch 기본 모니터링에서 확인한다.
애플리케이션 지표는 Grafana Cloud의 Metrics Endpoint 기능으로 60초마다 직접 수집한다.

## 1. 배포 전에 준비할 값

- 운영 API의 외부 HTTPS 주소. 기존 프록시에서 `/actuator/prometheus` GET 요청과 Authorization 헤더 전달이 가능해야 한다.
- Grafana Cloud Free 계정과 스택.
- 로그인 JWT 키와 다른 `MONITORING_TOKEN`. 운영 서버에서 `openssl rand -base64 32`로 생성해 비밀 환경 변수로 저장한다. 토큰을 Git·문서·채팅에 붙이지 않는다.

일반 사용자 쿠키와 JWT는 지표 경로에 사용할 수 없다. 수집 토큰도 사용자 API 인증에 사용할 수 없다.
인증은 메모리에서 해시 비교로 처리하며 DB나 인증 서버를 호출하지 않는다.

## 2. 운영 환경 변수와 배포

기존 운영 프로필에 `monitoring`을 추가하고 토큰을 컨테이너에 전달한다.

```dotenv
SPRING_PROFILES_ACTIVE=prod,monitoring
MONITORING_TOKEN=<운영 서버에서 생성한 전용 토큰>
```

`monitoring` 프로필이 없으면 기본 설정에서는 지표 등록과 Prometheus 내보내기가 꺼져 있다.
프로필을 켰는데 토큰이 없거나 43~256자 범위를 벗어나거나 Bearer 인증에서 사용할 수 없는 문자가 있으면 기동에 실패한다.
영문·숫자와 `-._~+/`, 끝의 `=` 패딩만 허용한다. 공백·줄바꿈·한글·콜론 등은 허용하지 않는다.
기존 DB·JWT 환경 변수도 계속 필요하다. 저장소의 docker-compose.yml은 local 프로필용이므로
실제 운영 배포 설정에 위 두 변수를 추가해야 한다. 별도 수집 포트를 공개할 필요는 없다.

prod의 HTTPS 강제 설정을 유지한다. 프록시의 전달 헤더를 신뢰하는 현재 구성에서는
백엔드 포트에 외부가 직접 접근하지 못하도록 보안 그룹·프록시를 설정해야 한다.
`/actuator/**` 전체를 공개하지 말고 필요한 지표 경로만 전달한다.

Docker 상태 확인은 `/actuator/health/readiness`를 사용한다. 이 경로는 `monitoring` 프로필이나 토큰 없이
GET으로 접근할 수 있으며, DB 연결만 점검해 `{"status":"UP"}` 또는 503과 `{"status":"DOWN"}`을 반환한다.
DB 이름·접속 정보·예외 상세는 응답에 포함하지 않는다. 다른 health 경로와 쓰기 메서드는 차단한다.
연구실 조회 쿼리를 실행하지 않고, 성공·실패 모두 일반 API 요청 지표에서 제외한다.
HTTPS 강제 설정은 유지하며 Docker 내부 요청은 기존처럼 `X-Forwarded-Proto: https`를 보낸다.
이번 변경은 새 이미지에 적용되므로 기존 컨테이너는 이미지 재빌드·교체가 필요하다.

## 3. Grafana Cloud 연결

1. Connections에서 `Metrics Endpoint` 연결을 추가한다.
2. URL에 `https://<운영 API 도메인>/actuator/prometheus`를 입력한다.
3. 인증 유형을 Bearer로 선택하고 전용 토큰을 입력한다.
4. Test Connection 성공 후 저장한다. 이 방식의 수집 간격은 60초다.
5. Explore에서 `up`과 `http_server_requests_seconds_count`를 확인한다.
   HTTP 지표는 실제 요청이 발생한 뒤 나타난다.

참고: [Grafana 직접 수집](https://grafana.com/docs/grafana-cloud/observe-and-act/send-data/metrics/metrics-prometheus/prometheus-config-examples/integration-guide/)

## 4. 첫 대시보드

Grafana Cloud가 부여한 job/instance 라벨을 확인하고 모든 쿼리를 해당 서비스로 제한한다.
아래 쿼리는 수집 대상이 SEBU 하나일 때의 시작 예제다. 새로고침은 60초로 설정한다.

| 패널 | PromQL |
| --- | --- |
| 수집 상태 | `up` |
| 분당 요청량 | `sum(rate(http_server_requests_seconds_count[5m])) * 60` |
| 최근 5분 서버 오류 건수 | `sum(increase(http_server_requests_seconds_count{status=~"5.."}[5m]))` |
| 핵심 조회 p95(초) | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{method="GET",status=~"2..",uri=~"/api/v1/laboratories|/api/v1/posts"}[5m])))` |
| JVM 힙 사용량 | `sum(jvm_memory_used_bytes{area="heap"})` |
| JVM 힙 상한 | `sum(jvm_memory_max_bytes{area="heap"} > 0)` |
| GC 시간(초/초) | `sum(rate(jvm_gc_pause_seconds_sum[5m]))` |
| DB 연결 사용·상한·대기 | `hikaricp_connections_active` / `hikaricp_connections_max` / `hikaricp_connections_pending` |
| DB 연결 타임아웃 | `sum(increase(hikaricp_connections_timeout_total[5m]))` |

- 요청량과 오류는 전체 API를 대상으로 한다. URI는 Spring이 매핑한 경로 템플릿으로 묶는다.
- 지연 분포를 위한 7개 경계는 GET `/api/v1/laboratories`, GET `/api/v1/posts`에만 적용한다.
  경계는 50·100·250·500·1000·2000·5000ms다. p95는 대략적인 추정치이며,
  특히 요청이 적거나 5초를 넘으면 정밀 분석에 적합하지 않다.
  나머지 API는 Prometheus의 동일 지표 형식 호환성을 위해 5초 경계 하나만 유지하며 p95 계산 대상에서 제외한다.
- 사용자 ID·검색어·개별 URL·예외 메시지를 라벨에 넣지 않는다. HTTP URI는 최대 100종으로 제한한다.
  한도를 넘은 새 URI 지표는 버려지므로 API가 크게 늘면 설정을 재검토한다.
- HTTP 계측은 `/api/` 요청에만 적용한다. Actuator와 문서 요청은 인증 실패를 포함해 계측 전에 제외한다.
  API 경로에서 매핑되지 않거나 보안 필터에서 거절된 요청의 UNKNOWN/NOT_FOUND 지표는 남긴다.
- 현재 Docker 헬스체크는 연구실 목록 API를 30초마다 호출하므로 해당 요청도 통계에 포함된다.
  사용자 트래픽과 구분되지 않으며, 저트래픽 환경에서 요청량·지연 해석에 유의한다.
- `up`은 지표 수집 경로의 상태다. 사용자 접속·DB 정상 여부를 보장하지 않는다.
- 서버 전체 RAM·파일시스템 여유 공간·학교 인증의 개별 실패 원인·배치 상태는 이번 초기 계측에 포함하지 않는다.
  로그인 API의 HTTP 상태별 건수는 기본 HTTP 지표에서 확인할 수 있다.

## 5. 초기 알림과 확인

먼저 수집 실패가 3분 지속될 때 알림을 연결하고, No Data·조회 오류도 알림 상태로 설정한다.
트래픽을 확인한 뒤 5xx 증가와 핵심 조회 지연 알림을 추가한다. 저트래픽에서는 오류 비율만으로 알리지 않고
최소 요청 건수와 지속 시간 조건을 함께 둔다. 수집 경로가 사용자 경로를 대신하지 않으므로
외부 접속 확인은 별도의 점검으로 보완한다.

배포 후 확인할 사항:

- 토큰 없이 지표 경로에 접근하면 401, 올바른 Bearer 토큰으로는 200.
- 상태 확인 GET 이외의 다른 Actuator 경로는 접근 불가. 기존 로그인·검색은 정상.
- Grafana에 실제 샘플이 들어오며 활성 시계열이 무료 한도 이내.
- 같은 트래픽 조건에서 배포 전후 CPU·컨테이너 메모리·응답 시간을 비교.

Grafana Cloud Free의 현재 공개 한도는 활성 시계열 10,000개·보관 14일이다.
무료 조건은 가입 시 다시 확인한다. CloudWatch는 AWS 콘솔에서 확인하며
Grafana의 CloudWatch 조회 연결은 초기 범위에 포함하지 않는다. 외부 전송량과 AWS 무료 한도도 확인한다.

- [Grafana 가격](https://grafana.com/pricing/)
- [CloudWatch 기본 모니터링](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/cloudwatch-metrics-basic-detailed.html)
- [CloudWatch 가격](https://aws.amazon.com/cloudwatch/pricing/)

## 로컬 검증

Java 21 환경에서 관련 테스트만 실행한다.

```powershell
.\gradlew.bat test --tests '*global.monitoring.*' --tests '*JwtSecurityIntegrationTest' --tests '*CookieCsrfIntegrationTest' --tests '*HttpsTransportIntegrationTest' --tests '*SecurityConfigurationNonWebTest' -PexcludeDocker --max-workers=1
```

실제 TLS·프록시·외부 수집 연결과 운영 부하는 배포 후 검증한다.
