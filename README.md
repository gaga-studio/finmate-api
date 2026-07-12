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

The application imports the repository-local `.env` file when it is present.
Environment variables supplied by the shell or deployment platform still take
precedence. The API is available at `http://localhost:8080/api/v1`.

Tests use PostgreSQL 16 through Testcontainers.

```bash
./gradlew test
```

The deterministic demo timeline is available only with the `demo` profile:

```bash
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
```

Canonical product documentation and the OpenAPI contract live under
[`docs/vnext`](docs/vnext/README.md).

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r docs/vnext/06-api/requirements.txt
python docs/vnext/06-api/verify_contracts.py
```
