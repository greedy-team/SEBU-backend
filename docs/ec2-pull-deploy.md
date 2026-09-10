# EC2 자동 배포 — 비공개 GHCR Pull 방식

## 1. 이번에 구축하는 범위

**기존 EC2를 유지하고, `develop`에 머지된 변경을 테스트한 뒤 EC2가 새 이미지를 가져와 배포한다.**

- PR 생성만으로 배포되지 않는다. PR에서는 테스트만 실행한다.
- `develop` 머지 → CI 성공 → 이미지 게시 → EC2 타이머 확인 순서다.
- GitHub Actions가 EC2에 SSH 접속하지 않는다. AWS OIDC·액세스 키·새 AWS 유료 서비스도 필요하지 않다.
- **이번 구현은 짧은 중단이 있는 단일 컨테이너 자동 배포다. 무중단 블루·그린은 후속 작업이다.**
- 설치만으로 배포가 시작되지 않는다. 첫 배포를 확인한 뒤 타이머를 별도로 켠다.

```text
로컬 코드 + 새 Flyway SQL 파일
           |
           v
PR 생성 --> 테스트 / 팀원 리뷰
           |
           v
develop 머지
           |
           v
GitHub Actions: 테스트 + Docker 빌드 + MySQL 8.0 검증
           |
           v
비공개 GHCR: sha-커밋SHA / develop 태그
           ^
           | EC2가 약 2분 간격으로 확인 (외부에서 SSH 접속하지 않음)
           |
EC2: 새 digest 확인 --> 이미지 다운로드 --> DB 백업
           |
           v
기존 BE 정지 --> 새 BE 실행 --> Flyway --> API 정상 응답 확인
           |
           +-- 성공: 배포 버전 기록, 재시작 정책 활성화
           |
           +-- 실패: 추가 자동 배포 차단, DB 변경 여부에 따라 복구/수동 점검

MySQL 컨테이너 + sebu-mysql-data 볼륨 + Caddy는 그대로 유지
```

## 2. 파일 역할

| 파일 | 역할 |
| --- | --- |
| `.github/workflows/ci.yml` | PR 테스트, develop 이미지 빌드·검증·비공개 GHCR 게시 |
| `ops/deploy/deploy.py` | 이미지 확인, 사전 점검, 백업, BE 교체, 상태·실패 관리 |
| `ops/deploy/install.sh` | EC2에 배포 도구와 systemd 파일 설치. 자동 배포는 켜지 않음 |
| `ops/deploy/login-ghcr.sh` | EC2에서 읽기용 토큰을 화면에 표시하지 않고 입력 |
| `ops/deploy/config.example.json` | 현재 서버 구조를 기준으로 한 설정 예시 |
| `ops/deploy/sebu-pull-deploy.timer` | 실행 완료 후 약 2분 간격으로 다음 확인 |
| `ops/deploy/smoke-prod.sh` | 실제 배포 DB와 분리된 MySQL 8.0으로 이미지 검증 |
| `ops/deploy/tests/test_deploy.py` | 배포 성공·실패·복구·중복 실행 방지 단위 테스트 |

배포 엔진은 Ubuntu 기본 Python 3 표준 라이브러리를 사용한다. 설치·인증·CI 검증은 Bash 스크립트로 실행한다.

## 3. 현재 EC2에 맞춘 설정 — 설치 전 다시 확인

| 항목 | 예상값 |
| --- | --- |
| EC2 | `sebu-dev-server`, Ubuntu, linux/amd64 |
| 백엔드 | `sebu-backend`, 768 MiB, `127.0.0.1:8080` |
| Docker 네트워크 | `sebu-network` |
| MySQL / DB | `sebu-mysql` / `sebu` |
| 보존할 DB 볼륨 | `sebu-mysql-data` → `/var/lib/mysql` |
| 공개 검증 API | `https://sebu-dev-api.duckdns.org/api/v1/laboratories` |
| 이미지 | `ghcr.io/greedy-team/sebu-backend:develop` |

위 값은 배포 준비 과정에서 확인했던 구조다. **실제 설치 시 사전 점검에 통과해야 하며, 다른 값이라면 설정을 먼저 검토한다.** 마운트·네트워크·포트·메모리·DB 대상이 다르면 기존 서비스를 임의로 교체하지 않는다.

현재 Caddy는 `caddy reverse-proxy --from ... --to sebu-backend:8080` 명령으로 실행된 구성이다. 이번 구현은 같은 백엔드 이름을 유지한다. 후속 블루·그린에서는 실제 Caddy 시작 설정까지 바꿔야 한다. 사용하지 않는 기본 Caddyfile만 수정해서는 영구 반영되지 않는다.

## 4. 비공개 GHCR 인증

### GitHub Actions

- 저장소에 자동 제공되는 `GITHUB_TOKEN`을 사용한다.
- 게시 job에만 `packages: write` 권한을 부여한다.
- 최초 패키지는 기본 비공개로 생성된다. 기존 패키지가 공개라면 게시를 중단한다.
- 기존 패키지를 쓰는 경우 패키지 설정의 Actions 접근에 이 저장소가 허용되어 있어야 한다.
- 다른 팀원이 패키지를 공개로 바꾸지 않도록 접근 권한도 관리한다. 공개 패키지는 다시 비공개로 돌릴 수 없으므로 주의한다.

### EC2

1. GitHub에서 **Personal access token (classic)**을 만든다.
2. `read:packages`만 선택하고 만료일을 정한다. 해당 사용자에게 패키지 읽기 권한이 있어야 한다.
3. 조직에서 SSO를 요구하면 토큰의 조직 사용도 승인한다.
4. EC2에서 아래 로그인 스크립트를 실행하고 토큰을 **숨김 입력**한다.

```bash
sudo bash /opt/sebu-deploy/login-ghcr.sh
```

토큰을 채팅·GitHub 파일·명령행 인수에 붙여 넣지 않는다. 기본 Docker 설정은 자격 증명을 암호화 저장하지 않으므로 root 전용 권한으로 보호한다. 토큰 만료·회수 시 동일 스크립트로 다시 로그인한다. AWS 자격 증명은 넣지 않는다.

참고: 저장소 자체는 공개이므로, 비공개 이미지라고 해서 GitHub에 커밋한 SQL까지 비공개가 되는 것은 아니다. SQL에는 공개 가능한 검증 데이터만 넣고 비밀번호·토큰·회원 데이터·전체 DB 백업을 커밋하지 않는다.

## 5. 설치 및 최초 활성화 순서

### 5-1. 코드 리뷰와 머지

1. 이 변경의 PR 테스트를 확인한다.
2. 팀 리뷰 후 `develop`에 머지한다.
3. Actions의 `Publish private develop image`까지 성공했는지 확인한다.
4. GitHub Packages에서 `sebu-backend`가 **Private**인지 확인한다.

첫 이미지 게시 때까지 EC2 타이머는 꺼 둔다. PR 브랜치의 이미지는 게시하지 않는다.

### 5-2. EC2에 배포 도구만 설치

이미 연결된 **EC2 Ubuntu 터미널**에서 실행한다. 아래 clone은 해당 폴더가 없는 최초 설치용이다.

```bash
git clone --branch develop --single-branch https://github.com/greedy-team/SEBU-backend.git ~/sebu-deploy-source
cd ~/sebu-deploy-source
git log -1 --oneline
sudo bash ops/deploy/install.sh
```

설치 결과:

- `/opt/sebu-deploy/`: 실행 파일
- `/etc/sebu-deploy/config.json`: 서버 설정, root 전용
- `/etc/sebu-deploy/backend.env`: 기존 BE 컨테이너 환경을 복사한 비밀 설정, root 전용
- `/var/lib/sebu-deploy/`: 배포 상태·실패 기록·DB 백업
- systemd 서비스·타이머 등록. **자동 시작은 하지 않음**

기존 환경 파일은 덮어쓰지 않는다. `prod`, Flyway 활성화, Hibernate `validate`를 확인한다. `.env`의 `$`, `#`, `=` 등을 셸 코드로 실행하지 않고 문자 그대로 Docker에 전달한다.

### 5-3. 인증 후 읽기 점검

```bash
sudo bash /opt/sebu-deploy/login-ghcr.sh
sudo python3 /opt/sebu-deploy/deploy.py check
```

`check`는 현재 BE와 DB 구조, 디스크 여유, 레지스트리 접근 및 대상 digest를 확인한다. 컨테이너를 교체하거나 DB 마이그레이션을 실행하지 않는다. 표시된 digest와 게시된 커밋을 확인한다.

### 5-4. 첫 배포는 중단 시간을 공유한 뒤 수동 실행

**백업 복구 절차를 확인하고, 팀에 짧은 중단을 알린 후 실행한다.** 아래 `sha256:...`는 `check`에서 확인한 전체 digest로 교체한다.

```bash
sudo python3 /opt/sebu-deploy/deploy.py deploy --expected-digest sha256:...
sudo python3 /opt/sebu-deploy/deploy.py status
```

이 단계부터 실제 BE가 교체되고 새 Flyway SQL이 서버 DB에 적용된다. MySQL·Caddy 컨테이너와 DB 볼륨은 삭제하지 않는다. 성공 후 API 응답뿐 아니라 FE 로그인·목록·상세 조회도 확인한다.

### 5-5. 최초 배포 성공 후 자동 확인 활성화

```bash
sudo touch /etc/sebu-deploy/enabled
sudo chmod 600 /etc/sebu-deploy/enabled
sudo systemctl enable --now sebu-pull-deploy.timer
sudo systemctl list-timers sebu-pull-deploy.timer
sudo journalctl -u sebu-pull-deploy.service -n 50 --no-pager
```

앞으로 테스트를 통과한 develop 이미지가 바뀌면 다음 확인 시 배포된다. 변경이 없으면 재시작하지 않는다. 여러 머지가 빠르게 들어오면 중간 버전은 건너뛰고 최신 대상이 선택될 수 있다. Flyway 새 파일을 누적 보관하므로 그 이미지에 포함된 미적용 마이그레이션을 순서대로 적용한다.

## 6. 로컬 크롤링 데이터와 Flyway — B안 작업법

1. 로컬에서 크롤링한 데이터를 검증한다.
2. 필요한 스키마 변경과 확정 데이터의 `INSERT`/`UPDATE`를 **새 버전 SQL 파일**로 작성한다.
3. 기존 적용 파일을 수정하지 않고, 팀원과 겹치지 않는 다음 버전 번호를 사용한다.
4. SQL 파일과 관련 JPA·API 코드를 함께 PR에 올린다.
5. CI의 테스트 DB에서 실행을 검증하고 develop에 머지한다.
6. EC2의 새 BE가 시작될 때 Flyway가 **서버 DB에 아직 적용하지 않은 파일**을 실행한다.

**로컬 MySQL 안에서만 수정한 값은 자동 전송되지 않는다.** SQL 파일 작성/변환 및 Git 커밋이 필요하다. GHCR은 MySQL 데이터 볼륨이 아니라 애플리케이션과 SQL 파일이 들어 있는 이미지를 보관한다. 로컬에서 이미 실행한 SQL과 서버에서 실행할 이력도 서로 별개다.

크롤링 데이터에는 안정적인 식별자·중복 처리 기준·외래키 순서를 정한다. CI는 새 DB로 검증하므로 기존 서버 데이터와의 충돌까지 증명하지 않는다. 중요한 변경은 익명화한 기존 데이터 복제본에서도 먼저 검증한다. 회원 가입 등 서비스 운영 데이터는 서버에 남고 로컬 자료로 덮어쓰지 않는다.

## 7. 실패 시 동작과 복구

| 상황 | 자동 동작 |
| --- | --- |
| 인증 실패·설정 불일치·이미지 메타데이터 오류 | 기존 BE를 정지하지 않고 오류 보고 |
| 백업 실패·백업 후 용량 부족 | 기존 BE 유지, 추가 배포 차단 |
| 다운로드/백업 중 새 버전 게시 | 기존 BE 유지, 다음 확인에서 새 버전 재평가 |
| 새 BE 실패, 이전/신규 Flyway SQL 동일 | 실패한 새 BE를 정지·제거하고 보존한 이전 BE 시작 시도 |
| 새 BE 실패, Flyway SQL 변경됨 | 새 BE 정지, DB 적용 상태 수동 확인. 이전 BE/DB를 자동 복구하지 않음 |
| 배포 도중 에이전트 종료·서버 장애 | 진행 기록을 남기고 다음 배포 차단. 자동 재실행하지 않음 |

새 BE는 건강 상태 확인 전까지 Docker 자동 재시작도 꺼 두므로 실패한 마이그레이션이 재시작 루프로 반복되지 않는다. 단, 같은 SQL이라도 애플리케이션의 다른 시작 로직이 DB를 수정한다면 코드 복구만으로 원복되지 않는다. Flyway 외 시작 시 DB 변경 로직은 별도로 리뷰한다.

긴급 중단은 다음과 같다. 진행 중인 배포를 강제 종료하는 명령이 아니다.

```bash
sudo systemctl disable --now sebu-pull-deploy.timer
sudo rm -- /etc/sebu-deploy/enabled
sudo systemctl status sebu-pull-deploy.service --no-pager
sudo python3 /opt/sebu-deploy/deploy.py status
```

로그와 `blocked.json`/`in-progress.json`, DB의 `flyway_schema_history`, 실제 테이블을 확인한다. 실패 기록에는 백업 경로와 이전 컨테이너 이름이 남는다. **`flyway repair`나 DB 복원을 무조건 실행하지 않는다.** MySQL DDL은 일부 반영됐을 수 있다. 이전 코드 실행 가능 여부 또는 새 수정 배포 여부를 판단한다.

복구를 완료한 뒤에만 해당 실패 digest를 지정해 차단을 해제한다. 이 명령 자체는 DB나 컨테이너를 수정하지 않는다.

```bash
sudo python3 /opt/sebu-deploy/deploy.py acknowledge-failure --expected-digest sha256:...
```

동일하게 실패하는 이미지를 다시 배포하지 않도록 수정된 develop 이미지와 현재 서버 상태를 확인한다. 복구 직후 수동 배포를 검증하고 타이머를 다시 활성화한다.

## 8. 백업·용량·운영 주의

- 배포 전 `mysqldump --single-transaction`으로 해당 DB를 백업하고 gzip 파일을 끝까지 읽어 압축 무결성을 확인한다. **복원 성공을 검증한 것은 아니므로 별도 복원 연습이 필요하다.**
- 백업 동안 서비스는 실행 중이므로 백업 이후 발생한 쓰기는 백업에 없다. DB 복원은 그 데이터를 잃을 수 있어 자동화하지 않는다. InnoDB와 동시 DDL이 없는 조건을 전제로 한다.
- 덤프·기존 이미지·중지된 이전 컨테이너는 자동 삭제하지 않는다. 디스크 여유 2 GiB 미만이면 새 배포를 중단한다. 정기적으로 용량을 확인하고 검토한 오래된 것만 개별 정리한다.
- `docker compose down -v`, `docker volume prune`, 전체 DB 덮어쓰기를 배포 절차에 넣지 않는다.
- 같은 EC2 디스크의 백업은 인스턴스·디스크 유실에 대비한 외부 백업이 아니다. 외부 백업은 저장 위치·암호화·비용을 정한 뒤 별도로 추가한다.
- GitHub develop 쓰기 권한은 서버 코드 및 SQL 실행 권한과 연결된다. 브랜치 보호와 PR 리뷰·CI 통과를 필수로 설정하는 것이 좋다.
- SSH는 관리자 접속용 내 IP만 허용한다. GitHub Actions 때문에 22번 포트를 전체 공개할 필요가 없다.
- 타이머 오류는 journal에 남는다. 알림·모니터링 대시보드는 아직 별도 작업이다.

## 9. 비용과 후속 무중단 전환

이 구현은 EC2 추가 생성이나 AWS 요금제 변경을 하지 않는다. 공개 저장소의 표준 GitHub-hosted Actions 러너는 무료이고, GHCR 컨테이너 이미지 저장·대역폭은 현재 무료 정책이다. **기존 EC2·EBS·공인 IP·네트워크 비용까지 영구 무료라는 뜻은 아니다.** 크레딧과 정책을 계속 확인한다.

블루·그린 전환에는 기존/신규 BE 동시 실행, 새 BE 건강 상태 검증, Caddy 요청 전환, 기존 요청 완료 후 종료가 추가된다. 현재 약 2 GiB EC2에서 MySQL과 BE 두 개를 동시에 실행할 메모리가 충분한지 먼저 측정한다. DB는 공유되므로 구·신 버전이 함께 사용할 수 있도록 스키마를 단계적으로 확장하고, 삭제·이름 변경 같은 비호환 변경을 별도 배포로 분리해야 한다.

### 공식 참고 자료

- [GitHub Container Registry 인증 및 사용](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [패키지 접근 권한과 공개 범위](https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility)
- [GitHub Actions 요금 정책](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [GitHub Packages 요금 정책](https://docs.github.com/en/billing/concepts/product-billing/github-packages)
- [Spring Boot 3.5 데이터 초기화와 Flyway](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)
