# nestor-charter — Visualization Agent

Generates Recharts-compatible chart data for the frontend dashboard.

## How It Works

1. Receives `{ job_id, portfolio_data }` from Planner
2. Builds a prompt describing the portfolio allocations, holdings, and totals
3. Calls Bedrock in **plain-text mode** (not tool-use), asking for JSON chart definitions
4. Parses the JSON from the response text
5. Re-keys charts by their `key` field for easy frontend lookup
6. Saves `charts_payload` to Aurora

## Output Format

Each chart is a JSON object compatible with Recharts components:

```json
{
  "asset_allocation": {
    "key": "asset_allocation",
    "chart_type": "pie",
    "title": "Asset Class Allocation",
    "data": [
      { "name": "Equity", "value": 70 },
      { "name": "Fixed Income", "value": 30 }
    ]
  }
}
```

## Lambda Settings

- **Timeout**: 5 minutes
- **Memory**: 1024 MB
- **Invoked by**: Planner (synchronous Lambda invoke)
