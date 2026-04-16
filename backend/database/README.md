# database — Schema Migrations & Seed Data (Python)

Python utilities for managing the Aurora Serverless v2 PostgreSQL database. Uses `uv` for dependency management.

## Scripts

| Script | Purpose |
|--------|---------|
| `run_migrations.py` | Apply SQL migrations from `migrations/` in order |
| `seed_data.py` | Load 22 pre-configured ETFs + sample instruments |
| `reset_db.py` | Drop and recreate all tables |
| `verify_database.py` | Check database health and table counts |
| `test_data_api.py` | Test Aurora Data API connectivity |
| `check_db.py` | Quick database status check |
| `update_region_targets.py` | Update region allocation targets |

## Database Schema

### Tables

- **`users`** — `clerk_user_id` (PK), display name, retirement config, allocation targets (JSONB)
- **`instruments`** — `symbol` (PK), name, type, price, allocation breakdowns (JSONB)
- **`accounts`** — UUID PK, linked to user, cash balance, interest rate
- **`positions`** — UUID PK, links account ↔ instrument with quantity
- **`jobs`** — Tracks async analysis: status, payloads (report, charts, retirement as JSONB)

### Migrations

SQL files in `migrations/` are applied in alphabetical order. Each creates or alters tables.

## Usage

```bash
cd backend/database

# Install dependencies
uv sync

# Run migrations
uv run run_migrations.py

# Seed instruments
uv run seed_data.py

# Verify database
uv run verify_database.py
```

Requires `AURORA_CLUSTER_ARN`, `AURORA_SECRET_ARN`, and `AURORA_DATABASE` in the root `.env` file.
