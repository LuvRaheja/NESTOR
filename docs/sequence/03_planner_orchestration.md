# Sequence Diagram 03 — Planner Orchestration

> Shows how `nestor-planner` acts as the central orchestrator. It uses **AWS Bedrock Converse API tool-calling** in a multi-turn loop to decide which sub-agents to invoke, then fans out to them (potentially in parallel).

```mermaid
sequenceDiagram
    autonumber

    participant SQS      as SQS Queue
    participant Planner  as nestor-planner Lambda\n(AgentOrchestrator)
    participant Aurora   as Aurora PostgreSQL
    participant S3Vec    as S3 Vectors\n(Knowledge Base)
    participant Bedrock  as AWS Bedrock\n(Nova Pro / OSS 120B)
    participant Tagger   as nestor-tagger Lambda
    participant Reporter as nestor-reporter Lambda
    participant Charter  as nestor-charter Lambda
    participant Retire   as nestor-retirement Lambda

    %% ── Startup ──────────────────────────────────────────────────────
    SQS->>Planner: SQS event { job_id }
    Planner->>Aurora: UPDATE job status → "running"
    Planner->>Aurora: Load job, user profile, accounts, positions

    %% ── Pre-flight: Tagger (conditional) ─────────────────────────────
    Note over Planner,Tagger: Pre-flight — Classify missing instruments
    Planner->>Aurora: Query instruments missing allocation data
    alt One or more instruments are unclassified
        Planner->>Tagger: Lambda.invoke()\n{ instruments: [{symbol, name}] }
        Tagger->>Bedrock: Converse + toolSpec\n(structured JSON output)
        Bedrock-->>Tagger: Classification JSON\n(asset_class, regions, sectors)
        Tagger->>Aurora: UPSERT instruments with allocations
        Tagger-->>Planner: Classification complete
    end

    %% ── Market Price Refresh ─────────────────────────────────────────
    Note over Planner,Aurora: Refresh market prices
    Planner->>Aurora: UPDATE instrument prices (best-effort)

    %% ── Build Portfolio Summary ──────────────────────────────────────
    Planner->>Aurora: Load full portfolio summary\n(positions, allocations, retirement params)
    Planner->>S3Vec: Retrieve relevant market research\n(semantic search)
    S3Vec-->>Planner: Top-K research snippets

    %% ── Bedrock Orchestration Loop (multi-turn tool-calling) ─────────
    Note over Planner,Bedrock: Bedrock tool-calling orchestration loop (max 10 turns)
    loop Until Bedrock returns text (no more tool calls)
        Planner->>Bedrock: ConverseRequest\n{ system prompt, messages, tool definitions }
        Bedrock-->>Planner: ConverseResponse\n{ toolUse blocks OR final text }

        alt Bedrock issues tool calls
            Note over Planner: Execute tool calls — potentially in parallel

            par invoke_reporter
                Planner->>Reporter: Lambda.invoke()\n{ job_id, portfolio_data, user_data }
                Reporter->>Bedrock: Generate narrative report
                Bedrock-->>Reporter: Markdown analysis text
                Reporter->>Aurora: UPSERT job result (report)
                Reporter-->>Planner: Report stored
            and invoke_charter
                Planner->>Charter: Lambda.invoke()\n{ job_id, portfolio_data }
                Charter->>Bedrock: Generate chart JSON
                Bedrock-->>Charter: Chart spec (pie/bar/line data)
                Charter->>Aurora: UPSERT job result (charts)
                Charter-->>Planner: Charts stored
            and invoke_retirement
                Planner->>Retire: Lambda.invoke()\n{ job_id, portfolio_data }
                Retire->>Bedrock: Monte Carlo + narrative
                Bedrock-->>Retire: Retirement projection text
                Retire->>Aurora: UPSERT job result (projections)
                Retire-->>Planner: Projections stored
            end

            Planner->>Bedrock: ConverseRequest\n{ tool results appended to messages }

        else Bedrock returns final text (no tool calls)
            Note over Planner: Orchestration complete
        end
    end

    %% ── Finalize ────────────────���───────────────────────────────────
    Planner->>Aurora: UPDATE job status → "completed"\nresult = { orchestration_summary }
    Note over Planner: Lambda returns success response
```

### Key Design Points

| Aspect | Detail |
|--------|--------|
| **Orchestration pattern** | Bedrock Converse API multi-turn tool-calling — the LLM decides which agents to call |
| **Parallelism** | Reporter, Charter, Retirement invoked via `CompletableFuture` fan-out |
| **Retry** | Resilience4j `Retry` wraps Bedrock calls; retries on `ThrottlingException` (5 attempts, 4s back-off) |
| **Max turns** | Hard cap of 10 conversation turns to prevent infinite loops |
| **Data flow** | All sub-agents read portfolio from Aurora; all write results back to Aurora |
| **Tagger is special** | Always runs **before** orchestration as a pre-flight step if any instrument lacks classification |

---

← [02 — User Analysis Flow](./02_user_analysis_flow.md) | Next: [04 — Research Ingestion Pipeline](./04_research_ingestion.md) →

