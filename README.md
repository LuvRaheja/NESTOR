# NESTOR — AI-Powered Financial Planning Platform

> **NESTOR** OR "Next-gen Engine for Savings, Taxation & Optimized Retirement" is an enterprise-grade, multi-agent financial planning SaaS platform deployed on AWS. 

---

## What It Does

NESTOR analyses users' equity portfolios and produces:
- **Portfolio reports** — AI-generated markdown narratives with insights and recommendations
- **Interactive charts** — Recharts-compatible JSON for allocation breakdowns, sector analysis, etc.
- **Retirement projections** — Monte Carlo simulations with accumulation and drawdown phases
- **Instrument classification** — Auto-tagging of ETFs/stocks with sector, region, and asset-class allocations

Users sign in via Clerk, manage accounts and positions through a React dashboard, and trigger AI analyses that run asynchronously on AWS Lambda.

---

## Architecture at a Glance

```
Browser (NextJS + Clerk auth)
    │
    ▼
CloudFront CDN ──► S3 Static Site
    │  /api/*
    ▼
API Gateway (HTTP API, JWT verification)
    │
    ▼
nestor-api Lambda (Java Spring Boot)
    │  SQS message
    ▼
SQS Queue (nestor-analysis-jobs)
    │
    ▼
nestor-planner Lambda (Orchestrator)
    ├── nestor-tagger   → classifies instruments → Aurora DB
    ├── nestor-reporter  → generates narrative    → Aurora DB
    ├── nestor-charter   → builds chart data      → Aurora DB
    └── nestor-retirement → Monte Carlo sims      → Aurora DB
             ↕
         AWS Bedrock (LLM)

EventBridge (scheduled)
    └── nestor-scheduler → Researcher (App Runner) → nestor-ingest → S3 Vectors
```

For detailed diagrams see [docs/](docs/) and [docs/sequence/](docs/sequence/).

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | NextJS (Pages Router), React, Recharts, Clerk Auth |
| **Backend (agents)** | Java 21, Spring Boot 3.x, Spring Cloud Function |
| **Shared library** | `nestor-common` — Bedrock client, Data API client, models |
| **LLM** | AWS Bedrock (Nova Pro / OpenAI OSS 120B via inference profiles) |
| **Database** | Aurora Serverless v2 PostgreSQL, Data API (HTTP, no VPC) |
| **Vector store** | S3 Vectors + SageMaker Serverless (all-MiniLM-L6-v2) |
| **Orchestration** | SQS, Lambda invoke, Bedrock tool-calling |
| **Infrastructure** | Terraform (independent per-guide directories) |
| **Build** | Maven multi-module, Docker container images, ECR |

---

## Directory Structure

```
NESTOR/
├── backend/                # All backend code (Java + Python database tools)
│   ├── pom.xml             # Maven parent POM (multi-module)
│   ├── common/             # Shared library (Bedrock, DataAPI, models)
│   ├── api/                # REST API (Spring Boot on Lambda)
│   ├── planner/            # Orchestrator agent
│   ├── tagger/             # Instrument classification agent
│   ├── reporter/           # Portfolio narrative agent
│   ├── charter/            # Chart/visualization agent
│   ├── retirement/         # Monte Carlo retirement agent
│   ├── ingest/             # Document ingestion to S3 Vectors
│   ├── scheduler/          # EventBridge → Researcher bridge
│   └── database/           # Python — schema migrations, seed data
│
├── frontend/               # NextJS React application (Clerk auth)
│   ├── pages/              # Page components (dashboard, analysis, etc.)
│   ├── components/         # Reusable React components
│   └── lib/                # API client, config, utilities
│
├── terraform/              # Infrastructure as Code (independent dirs)
│   ├── 2_sagemaker/        # SageMaker embedding endpoint
│   ├── 3_ingestion/        # S3 Vectors + ingest Lambda
│   ├── 4_researcher/       # App Runner research service
│   ├── 5_database/         # Aurora Serverless v2
│   ├── 6_agents/           # Lambda agents + SQS
│   ├── 7_frontend/         # CloudFront, S3, API Gateway
│   └── all/                # Combined deployment (optional)
│
├── docs/                   # Architecture docs & sequence diagrams
├── guidesJava/             # Step-by-step deployment guides (1–8)
├── scripts/                # Python deploy/destroy/local-dev scripts
├── .env.example            # Template for root environment variables
└── nestor-lambdas.openapi.yaml  # OpenAPI spec for Lambda invocations
```

---

## Prerequisites

- **AWS Account** with IAM user (`aws configure` completed)
- **Java 21** and **Maven 3.9+**
- **Docker Desktop** (for building Lambda container images)
- **Node.js 18+** and **npm** (for the frontend)
- **Python 3.12+** and **uv** (for database scripts)
- **Terraform 1.5+**
- **Clerk account** (free tier) at [clerk.com](https://clerk.com)
- **AWS Bedrock model access** granted for your chosen model

---

## Quick Start

### 1. Clone and configure environment

```bash
cp .env.example .env          # fill in your AWS account details
cp frontend/.env.local.example frontend/.env.local   # fill in Clerk keys
```

### 2. Follow the deployment guides

The guides in `guidesJava/` walk through deployment step-by-step:

| Guide | Topic |
|-------|-------|
| [1_permissions.md](guidesJava/1_permissions.md) | IAM setup |
| [2_sagemaker.md](guidesJava/2_sagemaker.md) | SageMaker embedding endpoint |
| [3_ingest.md](guidesJava/3_ingest.md) | S3 Vectors + ingestion Lambda |
| [4_researcher.md](guidesJava/4_researcher.md) | App Runner research agent |
| [5_database.md](guidesJava/5_database.md) | Aurora Serverless v2 |
| [6_agents.md](guidesJava/6_agents.md) | Multi-agent Lambda deployment |
| [7_frontend.md](guidesJava/7_frontend.md) | NextJS frontend + API Gateway |
| [8_enterprise.md](guidesJava/8_enterprise.md) | Monitoring & enterprise features |

### 3. Build the backend

```bash
cd backend
mvn clean package -DskipTests
```

### 4. Deploy infrastructure

Each terraform directory is independent. Deploy in guide order:

```bash
cd terraform/2_sagemaker
cp terraform.tfvars.example terraform.tfvars   # edit with your values
terraform init && terraform apply
# repeat for 3_ingestion, 4_researcher, 5_database, 6_agents, 7_frontend
```

### 5. Run the frontend locally

```bash
cd frontend
npm install
npm run dev
```

---

## Testing

```bash
# Local tests (mocked AWS services)
cd backend && mvn test

# Full integration tests against deployed infrastructure
cd backend
uv run test_full.py
uv run test_simple.py
```

Test payloads are in `backend/test_*.json`.

---

## Cost Management

Aurora Serverless v2 is the biggest recurring cost. Destroy it when not actively working:

```bash
cd terraform/5_database && terraform destroy
```

To tear down everything:

```bash
# Destroy in reverse order
cd terraform/7_frontend  && terraform destroy
cd terraform/6_agents    && terraform destroy
cd terraform/5_database  && terraform destroy
cd terraform/4_researcher && terraform destroy
cd terraform/3_ingestion && terraform destroy
cd terraform/2_sagemaker && terraform destroy
```

---

## India Localization

NESTOR includes full India localization: INR currency, Indian ETFs (NIFTYBEES, LIQUIDBEES, GOLDBEES, etc.), NPS/EPF account types, and India-specific retirement projections. See [docs/india_localization_checklist.md](docs/india_localization_checklist.md).

---