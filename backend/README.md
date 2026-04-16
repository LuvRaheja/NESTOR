# Backend — NESTOR Agent Services

This directory contains all backend code for the NESTOR platform: a Maven multi-module Java project plus a Python database utilities package.

## Module Overview

| Module | Type | Description |
|--------|------|-------------|
| `common/` | Library | Shared code — Bedrock client, DataAPI client, POJOs, utilities |
| `api/` | Lambda (Container) | REST API: Spring Boot handling user/account/job CRUD + SQS dispatch |
| `planner/` | Lambda (Container) | Orchestrator agent — decides which sub-agents to invoke via Bedrock tool-calling |
| `tagger/` | Lambda (Container) | Classifies instruments (sector, region, asset-class) using structured output |
| `reporter/` | Lambda (Container) | Generates portfolio analysis narratives with quality guardrails |
| `charter/` | Lambda (Container) | Produces Recharts-compatible chart JSON |
| `retirement/` | Lambda (Container) | Monte Carlo retirement projections and analysis |
| `ingest/` | Lambda (Container) | Embeds documents via SageMaker and stores in S3 Vectors |
| `scheduler/` | Lambda (Container) | EventBridge → App Runner bridge for scheduled research |
| `database/` | Python (uv) | Schema migrations, seed data, database verification scripts |

## Build

```bash
# Build all modules (from this directory)
mvn clean package -DskipTests

# Build a single module
mvn clean package -pl charter -am -DskipTests
```

Each module produces a Docker container image (`Dockerfile` per module).

## Architecture

All agents are **Spring Cloud Function** beans deployed as Docker container images in AWS Lambda (Java 21 runtime). They communicate through:

- **Lambda invoke** — Planner calls sub-agents synchronously
- **SQS** — API dispatches jobs to Planner
- **Aurora Data API** — All agents read/write results to PostgreSQL
- **Bedrock Converse API** — LLM calls with tool-use for orchestration and structured output

### Agent Collaboration Flow

```
API → SQS → Planner
              ├── Tagger (if new instruments)
              ├── Reporter → Aurora (report_payload)
              ├── Charter  → Aurora (charts_payload)
              └── Retirement → Aurora (retirement_payload)
```

## Configuration

All modules read configuration from environment variables via Spring `${ENV_VAR:default}` syntax in `application.properties`. Sensitive values (ARNs, API keys) have no defaults and **must** be set via environment variables or Lambda configuration.

Key environment variables:

| Variable | Used By | Source |
|----------|---------|--------|
| `AURORA_CLUSTER_ARN` | All agents | `terraform/5_database` output |
| `AURORA_SECRET_ARN` | All agents | `terraform/5_database` output |
| `DATABASE_NAME` | All agents | Usually `alex` |
| `BEDROCK_MODEL_ID` | All agents | Your Bedrock model |
| `BEDROCK_REGION` | All agents | Your AWS region |
| `POLYGON_API_KEY` | Planner | Polygon.io dashboard |
| `SAGEMAKER_ENDPOINT` | Reporter, Ingest | `terraform/2_sagemaker` output |
| `SQS_QUEUE_URL` | API | `terraform/6_agents` output |
| `CLERK_JWKS_URL` | API | Clerk dashboard |

## Test Payloads

Test JSON files (`test_*_payload.json` / `test_*_response.json`) are sample Lambda invocation payloads for manual testing:

- `test_planner_payload.json` — Trigger a full analysis by job ID
- `test_reporter_payload.json` — Direct reporter invocation with portfolio data
- `test_retirement_payload.json` — Direct retirement invocation
- `test_api_payload.json` — API Gateway event format

## Testing

```bash
# Unit tests
mvn test

# Integration tests (requires deployed infrastructure)
uv run test_full.py
uv run test_simple.py

# Multi-account stress test
uv run test_multiple_accounts.py

# Watch agent progress in real-time
uv run watch_agents.py
```
