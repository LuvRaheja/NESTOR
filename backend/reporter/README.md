# nestor-reporter — Portfolio Narrative Agent

Generates an AI-written markdown portfolio analysis report with insights and recommendations.

## How It Works

1. Receives `{ job_id, portfolio_data, user_data }` from Planner
2. Optionally fetches market insights from S3 Vectors (semantic search via SageMaker embeddings)
3. Builds a detailed prompt with portfolio holdings, allocations, and user preferences
4. Calls Bedrock for narrative generation
5. Runs **ReportJudge** guardrail — a second Bedrock call that scores report quality (0–1)
6. If score < threshold, regenerates the report
7. Saves `report_payload` to Aurora

## Guardrail: ReportJudge

The ReportJudge evaluates generated reports for:
- Accuracy and relevance to the portfolio
- Actionable recommendations
- Professional tone
- Appropriate disclaimers

Configurable threshold: `nestor.reporter.guard-score` (default: 0.3)

## Configuration

- `SAGEMAKER_ENDPOINT` — For fetching market insights (optional)
- `GUARD_AGAINST_SCORE` — Minimum quality score

## Lambda Settings

- **Timeout**: 5 minutes
- **Memory**: 1024 MB
- **Invoked by**: Planner (synchronous Lambda invoke)
