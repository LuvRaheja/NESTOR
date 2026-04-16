# Scripts — Deployment & Local Development

Python utilities for deploying, running locally, and destroying NESTOR infrastructure. All scripts use `uv`.

## Scripts

| Script | Purpose |
|--------|---------|
| `deploy.py` | Build frontend → upload to S3 → invalidate CloudFront |
| `run_local.py` | Start local development (frontend + API) |
| `destroy.py` | Tear down all Terraform infrastructure safely |

## Usage

```bash
cd scripts
uv sync              # install dependencies
uv run deploy.py     # deploy frontend
uv run destroy.py    # destroy infrastructure
uv run run_local.py  # local development
```

## Prerequisites

- Root `.env` file configured with AWS credentials and resource ARNs
- Terraform infrastructure deployed (for deploy/destroy)
- Node.js installed (for local frontend development)
