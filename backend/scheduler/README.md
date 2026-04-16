# nestor-scheduler — EventBridge → Researcher Bridge

A lightweight Lambda that fires on an EventBridge schedule and triggers the Researcher App Runner service to perform market research.

## How It Works

1. EventBridge fires a cron event (e.g., every 2 hours)
2. Scheduler Lambda receives the event
3. Sends an HTTP POST to the Researcher App Runner URL
4. Researcher performs autonomous web research using Bedrock + Playwright
5. Research results are ingested into S3 Vectors via the Ingest Lambda

## Configuration

- `APP_RUNNER_URL` — URL of the deployed Researcher App Runner service

## Lambda Settings

- **Timeout**: 30 seconds
- **Memory**: 256 MB
- **Trigger**: EventBridge Scheduler rule
- **Deployed via**: `terraform/4_researcher` (optional — `scheduler_enabled` flag)
