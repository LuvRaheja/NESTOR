# nestor-planner — Orchestrator Agent

The Planner is the **entry point** for all portfolio analyses. It receives a job ID via SQS, loads portfolio data from Aurora, and uses Bedrock **tool-calling** to decide which sub-agents to invoke.

## How It Works

1. Receives `{ "job_id": "..." }` from SQS
2. Loads job, user, accounts, positions, and instruments from Aurora
3. Handles missing instruments by invoking **Tagger** to classify them
4. Updates market prices via Polygon.io (best-effort)
5. Calls Bedrock with tools: `invoke_reporter`, `invoke_charter`, `invoke_retirement`
6. Bedrock decides which agents to call based on the user's request
7. Invokes chosen Lambdas synchronously and collects results
8. Updates job status in Aurora

## Tools Registered with Bedrock

| Tool | Target Lambda | Purpose |
|------|--------------|---------|
| `invoke_reporter` | `nestor-reporter` | Generate portfolio narrative |
| `invoke_charter` | `nestor-charter` | Generate chart data |
| `invoke_retirement` | `nestor-retirement` | Run Monte Carlo projections |

## Configuration

Key properties (`application.properties`):

- `nestor.planner.tagger-function` — Tagger Lambda name
- `nestor.planner.reporter-function` — Reporter Lambda name
- `nestor.planner.charter-function` — Charter Lambda name
- `nestor.planner.retirement-function` — Retirement Lambda name
- `nestor.planner.polygon-api-key` — Polygon.io API key (optional)

## Lambda Settings

- **Timeout**: 15 minutes (orchestrates multiple agents)
- **Memory**: 2048 MB
- **Trigger**: SQS queue `nestor-analysis-jobs`
