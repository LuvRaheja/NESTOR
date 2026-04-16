# nestor-common — Shared Library

Shared Java library used by all NESTOR agent modules. Contains clients, models, and utilities that every Lambda function needs.

## Key Components

### Clients
- **`BedrockClient`** — Wraps AWS Bedrock Converse API with retry (Resilience4j). Supports tool-calling and plain-text modes.
- **`DataApiClient`** — Wraps Aurora Data API for SQL execution. Supports transactions (begin/commit/rollback) and parameter binding.

### Models (POJOs)
- **`InstrumentClassification`** — Sector, region, and asset-class allocation maps
- **Job, User, Account, Position, Instrument** — Domain entities matching the database schema
- **Request/response DTOs** for inter-agent communication

### Repositories
- `UserRepository`, `AccountRepository`, `InstrumentRepository`, `PositionRepository`, `JobRepository`
- Each wraps DataApiClient for typed database operations

### Utilities
- **Tool-use infrastructure** — JSON schema generation for Bedrock structured output (the "tool-use trick")
- **`MarketPriceUpdater`** — Fetches live prices from Polygon.io (falls back to random)
- **Retry configuration** — Exponential backoff for Bedrock throttling

## Usage

This module is declared as a dependency in other modules:

```xml
<dependency>
    <groupId>com.nestor</groupId>
    <artifactId>nestor-common</artifactId>
</dependency>
```

Build: `mvn clean install -pl common`
