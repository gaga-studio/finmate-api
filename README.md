# FinMate API

FinMate vNext의 API, 금융 계산 정책, 제품 문서를 관리하는 기준 저장소입니다.

## Local development

Requirements:

- Java 21
- Docker

```bash
cp .env.example .env
docker compose up -d postgres
./gradlew bootRun
```

저장소 루트에 `.env`가 있으면 애플리케이션이 자동으로 불러옵니다. 셸이나
배포 환경에 설정한 환경변수가 `.env`보다 우선합니다. API 기본 주소는
`http://localhost:8080/api/v1`입니다.

### 로컬 Swagger

애플리케이션을 실행한 뒤 다음 주소에서 한국어 API 문서를 확인하고 직접
요청을 보낼 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI 원본: `http://localhost:8080/openapi/openapi.yaml`

인증이 필요한 API는 먼저 `Auth`의 회원가입 또는 로그인 API로 액세스 토큰을
발급받은 뒤, Swagger 우측 상단의 **Authorize**에 `Bearer` 접두어 없이 토큰만
입력합니다. Swagger는 [`docs/vnext/06-api/openapi.yaml`](docs/vnext/06-api/openapi.yaml)을
빌드 시 그대로 제공하므로 이 파일이 프론트엔드와 백엔드의 단일 계약입니다.

테스트는 Testcontainers를 통해 PostgreSQL 16을 사용합니다.

```bash
./gradlew test
```

결정적 데모 타임라인은 `demo` 프로필에서만 사용할 수 있습니다.

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

제품 기준 문서와 OpenAPI 계약은 [`docs/vnext`](docs/vnext/README.md)에 있습니다.

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r docs/vnext/06-api/requirements.txt
python docs/vnext/06-api/verify_contracts.py
```
