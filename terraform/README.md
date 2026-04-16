# Terraform — Infrastructure as Code

Each subdirectory is an **independent** Terraform project with its own state file. They are deployed in guide order and can be destroyed independently.

## Directory Map

| Directory | Guide | Resources Created |
|-----------|-------|-------------------|
| `2_sagemaker/` | Guide 2 | SageMaker Serverless endpoint (embedding model) |
| `3_ingestion/` | Guide 3 | S3 Vectors bucket, ingest Lambda, API Gateway + API key |
| `4_researcher/` | Guide 4 | App Runner service (Researcher), ECR repo, EventBridge scheduler |
| `5_database/` | Guide 5 | Aurora Serverless v2 PostgreSQL, Secrets Manager |
| `6_agents/` | Guide 6 | 5 Lambda agents (Planner/Tagger/Reporter/Charter/Retirement), SQS queue, ECR repos |
| `7_frontend/` | Guide 7 | CloudFront CDN, S3 static site, API Gateway, API Lambda |
| `all/` | — | Combined unified deployment (alternative to per-guide) |

## Usage

```bash
cd terraform/<directory>
cp terraform.tfvars.example terraform.tfvars  # edit with your values
terraform init
terraform plan
terraform apply
```

## Important Notes

- **State is local** (`terraform.tfstate`) — not stored remotely. Do not commit state files.
- Each directory has a `terraform.tfvars.example` — copy and fill in your values.
- `terraform.tfvars` files are gitignored (contain secrets/ARNs).
- Deploy in order: 2 → 3 → 4 → 5 → 6 → 7. Each guide may need outputs from earlier ones.
- Get outputs: `terraform output` in a directory to see ARNs and URLs.
- Destroy in reverse order when tearing down.

## Cost Warning

**Aurora Serverless v2** (`5_database/`) is the largest recurring cost. Destroy it when not actively working:

```bash
cd terraform/5_database && terraform destroy
```
