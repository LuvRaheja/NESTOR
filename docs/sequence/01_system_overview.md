# Sequence Diagram 01 — System Overview

> High-level end-to-end view of **all** NESTOR services. Two independent flows run concurrently:
> - **Research Pipeline** — background, scheduled every 2 hours, builds the knowledge base.
> - **User Analysis Pipeline** — user-triggered, produces a full portfolio analysis.

```mermaid
sequenceDiagram
    autonumber

    %% ─── Participants ───────────────────────────────────────────────
    actor       User        as 👤 User (Browser)
    participant Clerk       as Clerk Auth
    participant CF          as CloudFront CDN
    participant S3Site      as S3 Static Site
    participant APIGW       as API Gateway
    participant API         as nestor-api Lambda
    participant SQS         as SQS Queue
    participant Planner     as nestor-planner Lambda
    participant Agents      as Sub-Agents\n(tagger/reporter/charter/retirement)
    participant Bedrock     as AWS Bedrock
    participant AuroraPG    as Aurora PostgreSQL
    participant S3Vec       as S3 Vectors
    participant Scheduler   as nestor-scheduler Lambda
    participant Researcher  as Researcher (App Runner)
    participant Ingest      as nestor-ingest Lambda
    participant SageMaker   as SageMaker Endpoint

    %% ─── Research Pipeline (background, runs every 2 hours) ─────────
    Note over Scheduler,S3Vec: ⏱ Background Research Pipeline (every 2 hrs)
    loop Every 2 hours
        EventBridge->>Scheduler: Schedule trigger
        Scheduler->>Researcher: HTTP POST /research
        Researcher->>Bedrock: Generate market insights
        Bedrock-->>Researcher: Research content
        Researcher->>APIGW: POST /ingest (document)
        APIGW->>Ingest: Forward document
        Ingest->>SageMaker: Generate embedding
        SageMaker-->>Ingest: 384-dim vector
        Ingest->>S3Vec: Store vector + content
    end

    %% ─── User Analysis Pipeline ─────────────────────────────────────
    Note over User,AuroraPG: 🔵 User-Triggered Analysis Pipeline
    User->>CF: HTTPS Request
    CF->>S3Site: Serve static NextJS app
    S3Site-->>User: HTML/JS/CSS

    User->>Clerk: Sign In
    Clerk-->>User: JWT token

    User->>CF: POST /api/analyze (Bearer JWT)
    CF->>APIGW: Forward /api/* requests
    APIGW->>API: Invoke with JWT
    API->>Clerk: Validate JWT (JWKS)
    Clerk-->>API: Token valid
    API->>AuroraPG: Create job record
    API->>SQS: Enqueue job_id
    API-->>User: { job_id }

    SQS->>Planner: Trigger with job_id
    Planner->>AuroraPG: Load portfolio + user data
    Planner->>Bedrock: Orchestrate via tool-calling
    Bedrock-->>Planner: Tool call decisions

    Planner->>Agents: Invoke sub-agents (parallel)
    Agents->>Bedrock: LLM calls per agent
    Agents->>S3Vec: Retrieve relevant research
    Agents->>AuroraPG: Persist results (reports/charts/projections)

    Planner->>AuroraPG: Mark job = completed

    User->>CF: GET /api/jobs/{job_id}
    CF->>APIGW: Forward
    APIGW->>API: Invoke
    API->>AuroraPG: Fetch job + results
    API-->>User: Full analysis result
```

---

← [Back to Index](../README.md) | Next: [02 — User Analysis Flow](./02_user_analysis_flow.md) →

