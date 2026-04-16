# Building NESTOR: Part 2 - SageMaker Serverless Deployment

 SageMaker is a shared AWS service and does not depend on the backend language.

## What is built

- A SageMaker model using `all-MiniLM-L6-v2` from HuggingFace Hub
- A serverless endpoint that scales automatically
- Infrastructure as Code using Terraform

## Prerequisites

- Complete [1_permissions.md](1_permissions.md)
- Terraform installed (1.5+)

## Step 1: Configure Terraform Variables

```bash
# Use the SAME terraform directory as Alex — SageMaker is shared infrastructure
cd terraform/2_sagemaker

cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:
```hcl
aws_region = "us-east-1"
```

## Step 2: Deploy with Terraform

```bash
terraform init
terraform apply
```

This creates:
- IAM role for SageMaker
- SageMaker model configuration
- Serverless endpoint: `alex-embedding-endpoint`

## Step 3: Test the Endpoint

Verify the endpoint works:

```bash
cd backend/ingest
uv run test_search_s3vectors.py
```

## Step 4: Save Configuration

Update your `.env`:
```
SAGEMAKER_ENDPOINT=alex-embedding-endpoint
```

## Next Steps

Continue to [3_ingest.md](3_ingest.md) for the ingestion pipeline.
