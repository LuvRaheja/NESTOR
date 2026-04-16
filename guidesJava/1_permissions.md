# Building NESTOR: Part 1 - AWS Permissions Setup

Welcome to Project NESTOR — the Java port of Alex (Agentic Learning Equities eXplainer)!

NESTOR is the same AI-powered personal financial planner as Alex, but migrated from Python to Java with Spring Cloud Function, deployed as Docker container images on AWS Lambda.

## What is NESTOR?

NESTOR provides the same capabilities as Alex:
- Understand investment portfolios
- Plan for retirement
- Get personalized financial advice
- Track market trends and opportunities

The difference: NESTOR is built with **Java 21**, **Spring Boot 3.3**, and **Spring Cloud Function** instead of Python.

## Prerequisites

Before starting, ensure you have:
- An AWS account with root access
- AWS CLI installed and configured with your `aiengineer` IAM user
- Terraform installed (version 1.5 or later)
- **Java 21** (Amazon Corretto recommended)
- **Maven 3.9+**
- **Docker Desktop** installed and running

## Step 1: IAM Permissions

NESTOR uses the same AWS services as Alex, so the same IAM permissions apply. If you already completed the Alex permissions setup, you're good.

If not, follow these steps:

### 1.1 Create S3 Vectors Policy

1. Navigate to **IAM** → **Policies** → **Create policy**
2. Use JSON editor:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": ["s3vectors:*"],
            "Resource": "*"
        }
    ]
}
```

3. Name it `AlexS3VectorsAccess`

### 1.2 Create the AlexAccess Group

1. **IAM** → **User groups** → **Create group**
2. Group name: `AlexAccess`
3. Attach policies:
   - `AmazonSageMakerFullAccess`
   - `AmazonBedrockFullAccess`
   - `CloudWatchEventsFullAccess`
   - `AlexS3VectorsAccess`
4. Add your `aiengineer` user to this group

### 1.3 Additional ECR Permissions for NESTOR

NESTOR uses ECR for container images. Ensure your IAM user has:
- `AmazonEC2ContainerRegistryFullAccess` (for pushing images)

Add this to the `AlexAccess` group or attached directly to your user.

### 1.4 Verify Permissions

```bash
aws sts get-caller-identity
aws ecr describe-repositories
aws sagemaker list-endpoints
```

## Step 2: Project Setup

### Clone and Build

```bash
# Navigate to NESTOR directory
cd NESTOR

# Build all modules
mvn clean package -DskipTests
```

This builds:
- `nestor-common` — shared library
- `nestor-tagger` — instrument classification agent
- `nestor-scheduler` — EventBridge-to-AppRunner bridge

### Environment File

Create a `.env` file in the project root:
```
AWS_ACCOUNT_ID=123456789012
DEFAULT_AWS_REGION=us-east-1
```

## Next Steps

Continue to [2_sagemaker.md](2_sagemaker.md) to deploy the SageMaker embedding endpoint.
