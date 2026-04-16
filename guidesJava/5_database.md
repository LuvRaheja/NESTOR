# Building NESTOR: Part 5 - Database & Shared Infrastructure

The database layer is **shared infrastructure** between Alex and NESTOR. Both use the same Aurora Serverless v2 PostgreSQL cluster with the Data API. The database schema, seed data, and shared database library are identical.

## What is built

- Aurora Serverless v2 PostgreSQL cluster with Data API enabled
- Database schema for portfolios, users, and reports
- Seed data with 22 popular ETFs

## Why Aurora Serverless v2 with Data API?

The same reasons as Alex:
1. **No VPC Complexity** — Data API provides HTTP access
2. **Scales to Zero** — Reduces costs to ~$1.44/day minimum
3. **PostgreSQL** — Full SQL with JSONB support
4. **Data API** — Direct HTTP access from Lambda without connection pools

In NESTOR's Java code, the Aurora Data API is accessed via `software.amazon.awssdk:rds-data` (AWS SDK v2), wrapped in `nestor-common`'s `DataApiClient.java`.

## Prerequisites

- Completed Guides 1-4
- AWS CLI configured
- Terraform installed

## Step 0: Additional IAM Permissions

If not already done for Alex, create the custom RDS policy. See the original `guides/5_database.md` for the complete IAM JSON policy.

## Step 1: Deploy Database Infrastructure

```bash
# Use the SAME terraform directory — shared infrastructure
cd terraform/5_database
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:
```hcl
aws_region = "us-east-1"
```

Deploy:
```bash
terraform init
terraform apply
```

**Wait 10-15 minutes** for Aurora to fully initialize.

## Step 2: Save Important Outputs

```bash
terraform output
```

Save these values — they're needed by NESTOR Lambda environment variables:
- `aurora_cluster_arn`
- `aurora_secret_arn`
- `database_name` (should be `alex`)

Update your `.env`:
```
AURORA_CLUSTER_ARN=arn:aws:rds:us-east-1:123456789012:cluster:alex-aurora-cluster
AURORA_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:alex-aurora-credentials-xxxxx
DATABASE_NAME=alex
```

## Step 3: Initialize Database Schema and Seed Data

```bash
cd backend/database

# Run migrations to create tables
uv run run_migrations.py

# Load seed data (22 ETFs)
uv run seed_data.py

# Verify the database
uv run verify_database.py
```

## Step 4: Understanding the Java Database Layer

In NESTOR, the shared database library lives in `NESTOR/backend/common/`:

```
nestor-common/src/main/java/com/nestor/common/
├── db/
│   ├── DataApiClient.java          ← Aurora Data API wrapper
│   ├── DatabaseConfig.java         ← Spring @Configuration
│   └── repositories/
│       ├── UserRepository.java
│       ├── AccountRepository.java
│       ├── PositionRepository.java
│       ├── InstrumentRepository.java
│       └── JobRepository.java
└── model/
    ├── User.java
    ├── Account.java
    ├── Position.java
    ├── Instrument.java
    └── Job.java
```

Each NESTOR Lambda module depends on `nestor-common` to access the database.

## Cost Management

Aurora Serverless v2 is the biggest ongoing cost (~$1.44/day minimum). **Destroy it when not actively working**:

```bash
cd terraform/5_database
terraform destroy
```

Re-deploy and re-seed when you resume.

## Next Steps

Continue to [6_agents.md](6_agents.md) to deploy the AI agent orchestra.
