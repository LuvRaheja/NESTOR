# NESTOR Architecture Overview

## System Architecture

NESTOR uses the same serverless architecture as Alex, with one key difference: all Lambda functions are deployed as **Docker container images** (Java 21 + Spring Cloud Function) instead of Python ZIP packages.

```mermaid
graph TB
    %% API Gateway
    APIGW[API Gateway<br/>REST API<br/>API Key Auth]
    
    %% Backend Services
    Lambda[Lambda<br/>alex-ingest<br/>Document Processing]
    AppRunner[App Runner<br/>alex-researcher<br/>AI Agent Service]
    
    %% Scheduler
    EventBridge[EventBridge<br/>Scheduler<br/>Every 2 Hours]
    SchedulerLambda[Lambda<br/>nestor-research-scheduler<br/>Java 21 Container]
    
    %% AI Services
    SageMaker[SageMaker<br/>Embedding Model<br/>all-MiniLM-L6-v2]
    Bedrock[AWS Bedrock<br/>Nova Pro / OSS 120B]
    
    %% Data Storage
    S3Vectors[S3 Vectors<br/>Vector Storage<br/>90% Cost Reduction]
    ECR[ECR<br/>Docker Registry<br/>NESTOR Images]
    Aurora[(Aurora Serverless v2<br/>PostgreSQL<br/>Data API)]
    
    %% Agent Lambdas
    Tagger[Lambda<br/>nestor-tagger<br/>Java 21 Container]
    
    %% Connections
    AppRunner -->|Store Research| APIGW
    AppRunner -->|Generate| Bedrock
    APIGW -->|Invoke| Lambda
    
    EventBridge -->|Every 2hrs| SchedulerLambda
    SchedulerLambda -->|Call /research| AppRunner
    
    Lambda -->|Get Embeddings| SageMaker
    Lambda -->|Store Vectors| S3Vectors
    
    Tagger -->|Classify| Bedrock
    Tagger -->|Upsert| Aurora
    
    Tagger -.->|Pull Image| ECR
    SchedulerLambda -.->|Pull Image| ECR
    
    classDef aws fill:#FF9900,stroke:#232F3E,stroke-width:2px,color:#fff
    classDef java fill:#E76F00,stroke:#5382A1,stroke-width:2px,color:#fff
    classDef storage fill:#3B82F6,stroke:#1E40AF,stroke-width:2px,color:#fff
    classDef highlight fill:#90EE90,stroke:#228B22,stroke-width:3px,color:#000
    
    class APIGW,Lambda,AppRunner,SageMaker,ECR,SchedulerLambda aws
    class Tagger java
    class S3Vectors,Aurora storage
    class S3Vectors highlight
```

## Component Details

### NESTOR-Specific Components (Java)

| Component | Type | Image | Status |
|-----------|------|-------|--------|
| `nestor-tagger` | Lambda (Container) | `nestor-tagger:latest` | 
| `nestor-research-scheduler` | Lambda (Container) | `nestor-scheduler:latest` |
| `nestor-planner` | Lambda (Container) | `nestor-planner:latest` | 
| `nestor-reporter` | Lambda (Container) | `nestor-reporter:latest` | 
| `nestor-charter` | Lambda (Container) | `nestor-charter:latest` | 
| `nestor-retirement` | Lambda (Container) | `nestor-retirement:latest` |
| `nestor-ingest` | Lambda (Container) | `nestor-ingest:latest` |
| `nestor-api` | Lambda (Container) | `nestor-api:latest` |

### Shared Components (from Alex)

| Component | Type | Notes |
|-----------|------|-------|
| SageMaker endpoint | `alex-embedding-endpoint` | Embedding generation |
| S3 Vectors | `alex-vectors-{account-id}` | Vector storage |
| Aurora Serverless v2 | `alex-aurora-cluster` | Database |
| App Runner | `alex-researcher` | Research agent |
| API Gateway (ingest) | REST API | Ingest pipeline |
| CloudFront + S3 | Static site | Frontend |

## Technology Stack

| Layer | Alex | NESTOR |
|-------|------|--------|
| Language | Python 3.12 | Java 21 (Corretto) |
| Framework | OpenAI Agents SDK + LiteLLM | Spring Cloud Function + AWS SDK v2 |
| Build | uv + package_docker.py | Maven + Dockerfile |
| Package | ZIP → S3 → Lambda | Docker image → ECR → Lambda |
| AI Client | LiteLLM (Bedrock) | AWS SDK v2 BedrockRuntime |
| DB Client | Aurora Data API (boto3) | Aurora Data API (rds-data SDK) |
| Retry | tenacity | Resilience4j |
| JSON | Pydantic | Jackson |

## Deployment Model

```mermaid
graph LR
    Dev[Developer] -->|mvn package| JAR[Fat JAR]
    JAR -->|docker build| Image[Docker Image]
    Image -->|docker push| ECR[ECR Registry]
    ECR -->|image_uri| Lambda[Lambda Function]
    
    TF[Terraform] -->|Creates| ECR
    TF -->|Creates| Lambda
    TF -->|Creates| IAM[IAM Roles]
    
    style Image fill:#E76F00
    style ECR fill:#FF9900
    style Lambda fill:#FF9900
```

## Costs


| Component | Monthly Cost |
|-----------|-------------|
| S3 Vectors | ~$30 |
| SageMaker Serverless | ~$5-10 |
| Lambda (all agents) | ~$2-5 |
| App Runner | ~$5 |
| Aurora Serverless v2 | ~$43 (1.44/day) |
| API Gateway | ~$1 |
| ECR Storage | ~$1 |
| **Total** | **~$87-95** |

ECR adds minimal cost (~$0.10/GB/month for image storage).
