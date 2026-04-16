# Sequence Diagram 02 — User-Triggered Analysis Flow

> Detailed flow from the moment a user clicks **"Run Analysis"** in the browser through to retrieving completed results. Covers the frontend, API layer, async job dispatch, and result retrieval.

```mermaid
sequenceDiagram
    autonumber

    actor       User    as 👤 User (Browser)
    participant Clerk   as Clerk Auth
    participant FE      as NextJS Frontend
    participant CF      as CloudFront + API Gateway
    participant API     as nestor-api Lambda\n(Java Spring Boot)
    participant Aurora  as Aurora PostgreSQL
    participant SQS     as SQS Queue\n(nestor-analysis-jobs)
    participant Planner as nestor-planner Lambda

    %% ── Authentication ───────────────────��──────────────────────────
    Note over User,Clerk: Authentication
    User->>FE: Visit app (dashboard / accounts)
    FE->>Clerk: Redirect to sign-in
    Clerk-->>User: Issue JWT (Clerk session token)
    User->>FE: Authenticated session

    %% ── Portfolio Setup (pre-requisite) ─────────��───────────────────
    Note over User,Aurora: Portfolio Setup (done once)
    User->>CF: POST /api/users (profile)
    CF->>API: Invoke
    API->>Aurora: Upsert user record
    API-->>User: User profile saved

    User->>CF: POST /api/accounts (add account)
    CF->>API: Invoke
    API->>Aurora: Insert account + positions
    API-->>User: Account created

    %% ── Trigger Analysis ────────────────────────────────────────────
    Note over User,SQS: Trigger Portfolio Analysis
    User->>FE: Click "Run Analysis"
    FE->>CF: POST /api/analyze\n{ analysis_type: "portfolio" }\nAuthorization: Bearer <jwt>
    CF->>API: Forward request
    API->>Clerk: Verify JWT via JWKS endpoint
    Clerk-->>API: JWT valid, subject = clerk_user_id
    API->>Aurora: Create job record\n{ type: "portfolio_analysis", status: "pending" }
    Aurora-->>API: job_id (UUID)
    API->>SQS: SendMessage\n{ job_id, clerk_user_id, analysis_type }
    SQS-->>API: Message enqueued
    API-->>FE: HTTP 200 { job_id, message: "Analysis started" }
    FE-->>User: Show "Analysis in progress…" toast

    %% ── Async Job Processing ─────────────────────────────────────────
    Note over SQS,Planner: Async Processing (SQS → Lambda)
    SQS->>Planner: SQS event\n{ Records: [{ body: job_id }] }
    Planner->>Aurora: UPDATE job SET status = 'running'
    Note over Planner: (see Diagram 03 for full orchestration)
    Planner->>Aurora: UPDATE job SET status = 'completed', result = { ... }

    %% ── Result Polling ──��──────────���─────────────────────────────────
    Note over User,Aurora: Result Retrieval (polling)
    loop Poll every ~5 seconds
        FE->>CF: GET /api/jobs/{job_id}
        CF->>API: Invoke
        API->>Aurora: SELECT job WHERE id = job_id\nAND clerk_user_id = subject
        Aurora-->>API: Job row
        API-->>FE: { status: "running"|"completed", result: {...} }
        alt status = "completed"
            FE-->>User: Render analysis results\n(report, charts, retirement projections)
        else status = "running"
            FE-->>User: Keep showing loading state
        end
    end

    %% ── Error Path ──────��──────────���─────────────────────────────────
    Note over User,Planner: Error Handling
    opt Job fails
        Planner->>Aurora: UPDATE job SET status = 'failed', error = "..."
        FE-->>User: Display error message
    end
```

---

← [01 — System Overview](./01_system_overview.md) | Next: [03 — Planner Orchestration](./03_planner_orchestration.md) →

