# Sequence Diagram 05 — Instrument Classification (Tagger)

> Shows how the **Tagger** agent classifies financial instruments (ETFs, stocks, bonds) using **Bedrock's `toolSpec` trick** — forcing a tool call to guarantee structured JSON output — and persists the result to Aurora.

```mermaid
sequenceDiagram
    autonumber

    participant Caller   as Caller\n(nestor-planner OR direct invoke)
    participant Tagger   as nestor-tagger Lambda\n(Java 21)
    participant Bedrock  as AWS Bedrock\n(Nova Pro / OSS 120B)
    participant Aurora   as Aurora PostgreSQL

    %% ── Invocation ───────────────────────────────────────────────────
    Caller->>Tagger: Lambda.invoke()\n{ instruments: [{symbol, name, instrument_type}] }

    Note over Tagger: Iterate over each instrument
    loop For each instrument
        %% ── Bedrock Structured Output ────────────────────────────────
        Note over Tagger,Bedrock: Bedrock toolSpec trick — guaranteed JSON schema output
        Tagger->>Bedrock: ConverseRequest\n{\n  system: "You are a financial analyst…",\n  message: "Classify {symbol}: {name}",\n  toolConfig: { tools: [classifyInstrument toolSpec] },\n  toolChoice: { tool: classifyInstrument }\n}

        Note over Bedrock: Model MUST call the tool\n(forced via toolChoice)\nensuring JSON schema compliance

        Bedrock-->>Tagger: ToolUseBlock\n{\n  name: "classifyInstrument",\n  input: {\n    asset_class: "etf",\n    allocation_asset_class: { equity: 100 },\n    allocation_regions: { north_america: 95, … },\n    allocation_sectors: { technology: 30, … }\n  }\n}

        Note over Tagger: Validate allocations sum ~100\n(±3% tolerance)

        %% ── Persist to DB ────────────────────────────────────────────
        Tagger->>Aurora: UPSERT instrument\n{ symbol, name, instrument_type,\n  allocation_asset_class,\n  allocation_regions,\n  allocation_sectors,\n  current_price }
        Aurora-->>Tagger: Row upserted
    end

    Tagger-->>Caller: { classified_instruments: [...], status: "ok" }
```

### Bedrock Structured Output Pattern

The Tagger uses the **"toolSpec trick"**: instead of asking the LLM to return JSON in plain text (which can fail), it defines a JSON-schema tool and forces the model to call it via `toolChoice`. This guarantees the response conforms to the schema.

```
Tool definition (simplified):
{
  name: "classifyInstrument",
  inputSchema: {
    properties: {
      allocation_asset_class: { equity: %, fixed_income: %, … },
      allocation_regions:     { north_america: %, europe: %, … },
      allocation_sectors:     { technology: %, healthcare: %, … }
    }
  }
}
```

### Data Written to Aurora

| Column | Example |
|--------|---------|
| `symbol` | `VTI` |
| `name` | `Vanguard Total Stock Market ETF` |
| `instrument_type` | `etf` |
| `allocation_asset_class` | `{"equity": 100}` |
| `allocation_regions` | `{"north_america": 95, "international": 5}` |
| `allocation_sectors` | `{"technology": 30, "healthcare": 15, …}` |
| `current_price` | `250.00` |

---

← [04 — Research Ingestion Pipeline](./04_research_ingestion.md) | Next: [06 — Authentication & API Gateway](./06_auth_and_api.md) →

