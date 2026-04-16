# Sequence Diagram 04 — Research Ingestion Pipeline

> Shows the background knowledge-base build flow. **EventBridge** fires every 2 hours, triggering the scheduler Lambda which calls the **Researcher** (App Runner). The Researcher generates market insights which are ingested, embedded, and stored in **S3 Vectors** for later retrieval by the Planner.

```mermaid
sequenceDiagram
    autonumber

    participant EB        as EventBridge Scheduler\n(every 2 hours)
    participant Scheduler as nestor-scheduler Lambda\n(Java 21)
    participant Researcher as Researcher\n(App Runner — Python)
    participant Bedrock   as AWS Bedrock\n(LLM)
    participant IngestGW  as API Gateway\n(Ingest endpoint)
    participant Ingest    as nestor-ingest Lambda\n(Java 21)
    participant SageMaker as SageMaker Endpoint\n(all-MiniLM-L6-v2)
    participant S3Vec     as S3 Vectors\n(financial-research index)

    %% ── Scheduled Trigger ────────────────────────────────────────────
    Note over EB,Scheduler: Runs every 2 hours automatically
    EB->>Scheduler: Scheduled event (cron)
    Scheduler->>Researcher: HTTP POST /research\n{ trigger: "scheduled" }

    %% ── Researcher generates insights ───────────────────────────────
    Note over Researcher,Bedrock: AI-powered research generation
    Researcher->>Bedrock: Prompt: "Generate market insights / ETF analysis"
    Bedrock-->>Researcher: Research text (markdown)
    Note over Researcher: Splits content into chunks\nfor embedding

    %% ── Ingestion Loop (one chunk at a time) ────��──────────���─────────
    Note over Researcher,S3Vec: Document ingestion loop
    loop For each research chunk
        Researcher->>IngestGW: POST /ingest\n{ content, metadata }\nAPI Key auth
        IngestGW->>Ingest: Invoke Lambda

        Ingest->>SageMaker: InvokeEndpoint\n{ inputs: [chunk_text] }
        SageMaker-->>Ingest: [384-dim embedding vector]

        Ingest->>S3Vec: PutVectors\n{ vector, metadata: { source, date, topic } }
        S3Vec-->>Ingest: Stored
        Ingest-->>IngestGW: HTTP 200 { status: "ingested" }
        IngestGW-->>Researcher: Success
    end

    Researcher-->>Scheduler: HTTP 200 { status: "complete", chunks_ingested: N }
    Scheduler-->>EB: Lambda execution complete

    %% ── Later: Planner retrieves research ──────────��────────────────
    Note over S3Vec: 🔍 Later — used by nestor-planner
    Note over S3Vec: Planner calls QueryVectors with\nportfolio context embedding\nto retrieve top-K relevant insights
```

### Infrastructure Notes

| Component | Detail |
|-----------|--------|
| **EventBridge rule** | `rate(2 hours)` — configurable, can be disabled via `scheduler_enabled = false` in Terraform |
| **Scheduler Lambda** | Lightweight Java function; no AI, just HTTP POST trigger |
| **Researcher** | Python App Runner service shared with Alex; uses OpenAI Agents SDK + LiteLLM → Bedrock |
| **Ingest Lambda** | Java 21 Spring Cloud Function; generates embedding then upserts to S3 Vectors |
| **SageMaker** | `all-MiniLM-L6-v2` — 384-dimension sentence embeddings |
| **S3 Vectors** | `alex-vectors-{account-id}`, index `financial-research`, cosine similarity |
| **API Key** | Ingest API Gateway is key-protected to prevent unauthorized ingestion |

---

← [03 — Planner Orchestration](./03_planner_orchestration.md) | Next: [05 — Instrument Classification (Tagger)](./05_tagger_flow.md) →

