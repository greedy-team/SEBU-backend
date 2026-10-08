# SEBU Backend

학부 연구실 검색 서비스의 백엔드 애플리케이션입니다. Java 21 · Spring Boot · Gradle을 사용합니다.

## 프로젝트 문서

API 계약, 데이터 모델, 크롤링·승격, 실행·배포·로깅 안내는 [SEBU-brain](https://github.com/greedy-team/SEBU-brain)에서 관리합니다.

- [백엔드 문서 모음](https://github.com/greedy-team/SEBU-brain/blob/main/SEBU/90%20%EC%9E%90%EB%A3%8C/SEBU%20%EB%B0%B1%EC%97%94%EB%93%9C%20%EB%AC%B8%EC%84%9C%20%EB%AA%A8%EC%9D%8C.md)
- [로컬 실행 안내](https://github.com/greedy-team/SEBU-brain/blob/main/SEBU/05%20%EC%9A%B4%EC%98%81/SEBU%20%EB%A1%9C%EC%BB%AC%20%EC%8B%A4%ED%96%89.md)
- [기존 README의 상세 실행 안내](https://github.com/greedy-team/SEBU-brain/blob/main/SEBU/90%20%EC%9E%90%EB%A3%8C/%EB%B0%B1%EC%97%94%EB%93%9C%20%EB%AC%B8%EC%84%9C/SEBU%20%EB%B0%B1%EC%97%94%EB%93%9C%20%EC%8B%9C%EC%9E%91%20%EC%95%88%EB%82%B4%20%EC%9B%90%EB%AC%B8.md)

## 개발 시작

Java 21을 준비하고 `.env.example`을 참고해 `JWT_SECRET_BASE64` 등 실행 환경 변수를 설정합니다. 실제 `.env`와 비밀키는 커밋하지 않습니다.

```powershell
.\gradlew.bat bootRun
.\gradlew.bat test
```

macOS/Linux에서는 `./gradlew`를 사용합니다. 로컬 Swagger UI: `http://localhost:8080/swagger-ui/index.html`.

## 코드와 테스트 자료

- `src/main/`: 애플리케이션과 Flyway 마이그레이션
- `src/test/`: 자동 테스트와 검증용 fixture
- `ops/`, `.github/`: 운영 스크립트와 CI
- `.agents/skills/`: 백엔드 개발·마이그레이션 작업 지침

분류 CSV는 자동 테스트가 독립적으로 실행되도록 `src/test/resources/fixtures/`에 유지합니다. 과거 Flyway 파일의 `docs/...` 주석은 체크섬 보존을 위해 그대로 두며, 이전 위치는 브레인의 문서 모음에서 찾을 수 있습니다.
