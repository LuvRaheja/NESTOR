# nestor-api — REST API

Spring Boot REST API, deployed as a Docker container Lambda behind API Gateway.

## Responsibilities

- Authenticate requests via **Clerk JWT** tokens
- CRUD for users, accounts, and positions
- Trigger analysis jobs (sends message to SQS, which invokes Planner)
- Return job status and results to the frontend

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/user` | Get or create user (from JWT) |
| PUT | `/api/user` | Update user profile |
| GET | `/api/accounts` | List user's accounts |
| POST | `/api/accounts` | Create a new account |
| GET | `/api/accounts/{id}/positions` | Get positions in account |
| POST | `/api/jobs` | Trigger portfolio analysis |
| GET | `/api/jobs/{id}` | Get job status and results |
| POST | `/api/populate-test-data` | Seed demo data for user |

## Authentication

Every request must include `Authorization: Bearer <clerk-jwt>`. The subject claim is the `clerk_user_id` used to scope all data.

## Configuration

Required environment variables (set in Lambda or `.env`):

- `AURORA_CLUSTER_ARN` / `AURORA_SECRET_ARN` — database access
- `SQS_QUEUE_URL` — job dispatch queue
- `CLERK_JWKS_URL` — JWT verification

## Build & Deploy

```bash
mvn clean package -pl api -am -DskipTests
docker build -t nestor-api .
```

Deployed via `terraform/7_frontend`.
