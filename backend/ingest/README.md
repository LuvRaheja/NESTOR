# nestor-ingest — Document Ingestion Agent

Receives documents (research reports, articles), generates embeddings via SageMaker, and stores them in S3 Vectors for semantic search.

## How It Works

1. Receives `{ title, content, metadata }` via API Gateway
2. Calls SageMaker Serverless endpoint (all-MiniLM-L6-v2) to generate embeddings
3. Stores the vector + metadata in **S3 Vectors** bucket under the `financial-research` index
4. Used by Reporter agent for market insights (semantic search)

## Configuration

- `SAGEMAKER_ENDPOINT` — Embedding model endpoint name
- `VECTOR_BUCKET` — S3 Vectors bucket name
- `INDEX_NAME` — Vector index name (default: `financial-research`)

## API

Triggered via API Gateway with API key authentication:

```
POST /prod/ingest
X-API-Key: <api-gateway-key>

{
  "title": "Q4 Market Outlook",
  "content": "...",
  "metadata": { "source": "research-team", "date": "2026-04-01" }
}
```

## Lambda Settings

- **Timeout**: 2 minutes
- **Memory**: 512 MB
- **Deployed via**: `terraform/3_ingestion`
