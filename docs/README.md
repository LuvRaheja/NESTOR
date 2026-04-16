# NESTOR — Documentation Index

> **NESTOR** (Java port of Alex) is an AI-powered personal financial planner built on AWS serverless infrastructure. All Lambda functions are deployed as **Java 21 Docker container images** via Spring Cloud Function.

## 📚 Sequence Diagrams

| Diagram | Description |
|---------|-------------|
| [01 — System Overview](./sequence/01_system_overview.md) | End-to-end high-level flow: user → frontend → API → agents → results |
| [02 — User-Triggered Analysis Flow](./sequence/02_user_analysis_flow.md) | Complete portfolio analysis pipeline from browser to completed job |
| [03 — Planner Orchestration](./sequence/03_planner_orchestration.md) | How the Planner Lambda coordinates all sub-agents via Bedrock tool-calling |
| [04 — Research Ingestion Pipeline](./sequence/04_research_ingestion.md) | Scheduled research ingestion: EventBridge → Scheduler → Researcher → S3 Vectors |
| [05 — Instrument Classification (Tagger)](./sequence/05_tagger_flow.md) | How the Tagger classifies financial instruments using Bedrock structured output |
| [06 — Authentication & API Gateway](./sequence/06_auth_and_api.md) | Clerk JWT authentication and API Gateway request routing |
| [07 — Deployment Pipeline](./sequence/07_deployment_pipeline.md) | Developer build → Docker → ECR → Lambda deployment flow |

## 🏗 Architecture Summary

```
Browser (NextJS)
    │  HTTPS
    ▼
CloudFront CDN ──► S3 Static Site
    │  /api/*
    ▼
API Gateway (HTTP API)
    │  JWT (Clerk)
    ▼
nestor-api Lambda (Java Spring Boot)
    │  SQS message
    ▼
SQS Queue (nestor-analysis-jobs)
    │  trigger
    ▼
nestor-planner Lambda (Java 21 — Orchestrator)
    ├── nestor-tagger Lambda   →  Aurora DB
    ├── nestor-reporter Lambda →  Aurora DB
    ├── nestor-charter Lambda  →  Aurora DB
    └── nestor-retirement Lambda → Aurora DB
             ↕
         AWS Bedrock (Nova Pro / OSS 120B)

EventBridge (every 2hrs)
    │
    ▼
nestor-scheduler Lambda (Java 21)
    │  HTTP POST
    ▼
Researcher (App Runner)
    ├── AWS Bedrock
    └── API Gateway → nestor-ingest Lambda → SageMaker → S3 Vectors
```

## 🧩 Service Inventory

| Service | Type | Technology | Role |
|---------|------|-----------|------|
| **Frontend** | Static Site | NextJS / React | User interface |
| **CloudFront** | CDN | AWS | Static content delivery + API routing |
| **nestor-api** | Lambda (Container) | Java Spring Boot | REST API, auth, job dispatch |
| **API Gateway** | HTTP API | AWS | TLS termination, JWT verification |
| **SQS** | Queue | AWS | Decouples API from agent processing |
| **nestor-planner** | Lambda (Container) | Java 21, Spring Cloud Function | Orchestrator agent |
| **nestor-tagger** | Lambda (Container) | Java 21, Spring Cloud Function | Instrument classification |
| **nestor-reporter** | Lambda (Container) | Java 21, Spring Cloud Function | Portfolio narrative report |
| **nestor-charter** | Lambda (Container) | Java 21, Spring Cloud Function | Chart / visualization data |
| **nestor-retirement** | Lambda (Container) | Java 21, Spring Cloud Function | Monte Carlo retirement projections |
| **nestor-ingest** | Lambda (Container) | Java 21, Spring Cloud Function | Document embedding ingestion |
| **nestor-scheduler** | Lambda (Container) | Java 21, Spring Cloud Function | EventBridge-to-Researcher bridge |
| **Researcher** | App Runner | Python / OpenAI SDK | AI research agent |
| **Aurora Serverless v2** | Database | PostgreSQL | Persistent data store |
| **S3 Vectors** | Vector Store | AWS | Semantic search / knowledge base |
| **SageMaker** | ML Endpoint | all-MiniLM-L6-v2 | Embedding generation |
| **AWS Bedrock** | AI Service | Nova Pro / OSS 120B | LLM inference for all agents |
| **ECR** | Container Registry | AWS | Docker image storage |
| **Clerk** | Auth SaaS | Clerk.dev | JWT authentication |
| **EventBridge** | Scheduler | AWS | Cron trigger (every 2 hrs) |

## 🌏 India Localization

NESTOR has full India localization support. See [india_localization_checklist.md](./india_localization_checklist.md) for details covering:
- INR currency and Indian financial instruments
- NPS/EPF account types
- India-specific tax regimes and retirement projections
- Indian seed data (NIFTYBEES, LIQUIDBEES, GOLDBEES, etc.)

## 📖 Related Documentation

- [Root README](../README.md) — Project overview, quick start, directory structure
- [Deployment Guides](../guidesJava/) — Step-by-step guides 1–8
- [Backend README](../backend/README.md) — Agent modules, build instructions
- [Terraform README](../terraform/README.md) — Infrastructure overview
- [Frontend README](../frontend/README.md) — UI setup and pages
- [OpenAPI Spec](../nestor-lambdas.openapi.yaml) — Lambda invocation API