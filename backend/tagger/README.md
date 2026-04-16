# nestor-tagger — Instrument Classification Agent

Classifies financial instruments (ETFs, stocks) by sector, region, and asset class using Bedrock **structured output** via the tool-use trick.

## How It Works

1. Receives a list of `{ symbol, name, instrument_type }` objects
2. Defines a JSON schema matching `InstrumentClassification` fields
3. Forces Bedrock to call a tool whose input matches that schema — guaranteeing structured JSON output
4. Parses the result and upserts instrument allocations into Aurora

## Input/Output

**Input:**
```json
{
  "instruments": [
    { "symbol": "VTI", "name": "Vanguard Total Stock Market ETF", "instrument_type": "etf" }
  ]
}
```

**Output:** Classification written to `instruments` table with:
- `allocation_regions` — e.g. `{"north_america": 100}`
- `allocation_sectors` — e.g. `{"technology": 30, "healthcare": 15, ...}`
- `allocation_asset_class` — e.g. `{"equity": 100}`

## Lambda Settings

- **Timeout**: 5 minutes
- **Memory**: 1024 MB
- **Invoked by**: Planner (synchronous Lambda invoke)
