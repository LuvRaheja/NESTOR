# Building NESTOR: Part 3 - Ingestion Pipeline with S3 Vectors

This guide covers the Java-based ingestion pipeline. The `nestor-ingest` module generates embeddings via SageMaker and stores vectors in S3 Vectors, deployed as a container-based Lambda.

## What We're Building

- S3 Vectors bucket for vector storage (90% cheaper than OpenSearch)
- Java (Spring Cloud Function) Lambda for document ingestion
- ECR repository for the container image
- API Gateway with API key authentication
- Integration with the SageMaker embedding endpoint

## Prerequisites

- Completed Guides 1-2
- AWS CLI configured
- Terraform installed
- Docker Desktop running
- Maven installed (or use `./mvnw` wrapper)

## Step 1: Create S3 Vector Bucket

1. Go to [S3 Console](https://console.aws.amazon.com/s3/)
2. Click **"Vector buckets"** in left navigation
3. **Create vector bucket**: `alex-vectors-{your-account-id}`
4. Create an index:
   - Index name: `financial-research`
   - Dimension: `384`
   - Distance metric: `Cosine`

## Step 2: Build the Java Ingest Module

Build the fat JAR from the NESTOR backend root:

```bash
cd backend
mvn clean package -pl ingest -am -DskipTests
```

You should see:
```
[INFO] BUILD SUCCESS
```

The fat JAR is created at `backend/ingest/target/nestor-ingest-1.0.0-SNAPSHOT.jar`.

## Step 3: Configure Terraform and Create ECR Repository

```bash
cd ../terraform/3_ingestion

cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:
```hcl
aws_region = "us-east-1"
sagemaker_endpoint_name = "alex-embedding-endpoint"
```

Initialize Terraform and create just the ECR repository first (the Lambda needs an image to exist before it can be created):

```bash
terraform init
terraform apply -target=aws_ecr_repository.ingest -target=aws_ecr_lifecycle_policy.ingest
```

## Step 4: Build and Push the Container Image

Get the ECR repository URL from Terraform output:

**PowerShell (Windows):**
```powershell
$AWS_REGION = "us-east-1"
$ECR_URL = terraform output -raw ecr_repository_url

# Authenticate Docker with ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_URL

# Build the container image (from the ingest module directory)
# IMPORTANT: --provenance=false prevents OCI attestation manifests that Lambda rejects
cd ../../backend/ingest
docker build --platform linux/amd64 --provenance=false -t nestor-ingest .

# Tag the image for ECR
docker tag nestor-ingest:latest "<YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/nestor-ingest:latest"

# Push to ECR
docker push "<YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/nestor-ingest:latest"
```

**Bash (Mac/Linux):**
```bash
AWS_REGION="us-east-1"
ECR_URL=$(terraform output -raw ecr_repository_url)

# Authenticate Docker with ECR
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_URL

# Build the container image (from the ingest module directory)
# IMPORTANT: --provenance=false prevents OCI attestation manifests that Lambda rejects
cd ../../backend/ingest
docker build --platform linux/amd64 --provenance=false -t nestor-ingest .

# Tag the image for ECR
docker tag nestor-ingest:latest ${ECR_URL}:latest

# Push to ECR
docker push ${ECR_URL}:latest
```

## Step 5: Deploy Remaining Infrastructure

Now that the image is in ECR, deploy everything else (Lambda, API Gateway, S3 bucket, IAM):

```bash
cd ../../terraform/3_ingestion
terraform apply
```

Type `yes` when prompted. This creates:
- S3 Vectors bucket
- IAM role with SageMaker + S3 Vectors permissions
- Container-based Lambda function (`nestor-ingest`) using the image you just pushed
- API Gateway with API key auth

## Step 6: Save Configuration

Get your API key:
```bash
cd ../../terraform/3_ingestion
terraform output
```

Update `.env`:
```
VECTOR_BUCKET=alex-vectors-YOUR_ACCOUNT_ID
NESTOR_API_ENDPOINT=https://xxxxx.execute-api.us-east-1.amazonaws.com/prod/ingest
NESTOR_API_KEY=your-api-key-here
```

To retrieve the API key value:
```bash
aws apigateway get-api-key --api-key $(terraform output -raw api_key_id) --include-value --query 'value' --output text
```

## Step 7: Test

Test via direct Lambda invocation:
```bash
aws lambda invoke --function-name nestor-ingest --payload '{"text": "Tesla reported strong Q4 earnings driven by record vehicle deliveries.", "metadata": {"source": "test", "category": "earnings"}}' \
  --cli-binary-format raw-in-base64-out \
  response.json

cat response.json
```

You should see:
```json
{"statusCode": 200, "body": {"message": "Document indexed successfully", "document_id": "..."}}
```

Test via API Gateway:
```bash
curl -X POST $NESTOR_API_ENDPOINT \
  -H "x-api-key: $NESTOR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"text": "Amazon Web Services continues to dominate the cloud market.", "metadata": {"source": "api_test"}}'
```

Or with inline values (replace the placeholders from `terraform output`):
```powershell
$API_ENDPOINT = "https://xxxxx.execute-api.us-east-1.amazonaws.com/prod/ingest"
$API_KEY = "your-api-key-here"

curl -X POST $API_ENDPOINT `
  -H "x-api-key: $API_KEY" `
  -H "Content-Type: application/json" `
  -d '{\"text\": \"Amazon Web Services continues to dominate the cloud market.\", \"metadata\": {\"source\": \"api_test\"}}'
```

You should see:
```json
{"statusCode":200,"body":{"message":"Document indexed successfully","document_id":"..."}}
```

If you get a `403 Forbidden`, double-check the API key value. If you get a `500`, check CloudWatch logs:
```bash
aws logs tail /aws/lambda/nestor-ingest --follow
```

## Architecture Overview

```
Client ──▶ API Gateway (API key) ──▶ Lambda (nestor-ingest)
                                          │
                                          ├──▶ SageMaker Endpoint (embeddings)
                                          │         384-dim vectors
                                          └──▶ S3 Vectors (put-vectors via AWS CLI)
```

## Module Structure

```
backend/ingest/
├── pom.xml                          Maven module definition
├── Dockerfile                       Multi-stage Lambda container build
└── src/main/
    ├── java/com/nestor/ingest/
    │   ├── IngestConfig.java        Spring Boot app + bean wiring
    │   ├── IngestFunction.java      Function<Map,Map> entry point
    │   ├── EmbeddingClient.java     SageMaker embedding generation
    │   └── S3VectorsClient.java     S3 Vectors storage via AWS CLI
    └── resources/
        └── application.properties   Configuration
```

## Troubleshooting

### Docker build fails
- Is Docker Desktop running? Run `docker ps` to verify.

### Lambda timeout or "Task timed out"
- The function has a 60s timeout. If SageMaker endpoint is cold-starting, it may take longer on first invocation.
- Check CloudWatch logs: `aws logs tail /aws/lambda/nestor-ingest --follow`

### "S3 Vectors put-vectors failed"
- The Lambda uses `aws s3vectors put-vectors` via AWS CLI subprocess. Verify the AWS CLI is available in the container.
- Check that the `VECTOR_BUCKET` environment variable matches your S3 Vectors bucket name.

### "AccessDenied" errors
- Verify the Lambda IAM role has `s3vectors:PutVectors` and `sagemaker:InvokeEndpoint` permissions.
- Check that the SageMaker endpoint name in Terraform matches the actual endpoint.

### ECR push fails
- Re-authenticate: `aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <ECR_URL>`
- Ensure Docker is building for `linux/amd64` platform.

## Next Steps

Continue to [4_researcher.md](4_researcher.md) to deploy the research agent and scheduler.
