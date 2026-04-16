# Sequence Diagram 07 — Deployment Pipeline

> Shows how a NESTOR agent goes from Java source code to a live AWS Lambda function. The same pattern applies to **all Java Lambda modules** (tagger, planner, reporter, charter, retirement, ingest, scheduler, api).

```mermaid
sequenceDiagram
    autonumber

    actor       Dev     as 👨‍💻 Developer
    participant Maven   as Maven Build\n(local)
    participant Docker  as Docker Desktop\n(local)
    participant ECR     as AWS ECR\n(Container Registry)
    participant Lambda  as AWS Lambda
    participant TF      as Terraform\n(IaC)

    %% ── Infrastructure Provisioning (one-time) ──────────────────────
    Note over Dev,TF: One-time infrastructure setup
    Dev->>TF: terraform init && terraform apply\n(terraform/6_agents)
    TF->>ECR: Create ECR repository\n(e.g., nestor-tagger)
    TF->>Lambda: Create Lambda function\n(image_uri = placeholder)
    TF->>TF: Create IAM roles\n(Bedrock, Aurora, S3Vec, SQS permissions)
    TF-->>Dev: ECR URL, Lambda ARN

    %% ── Build Java Fat JAR ──────────────────────────────────────────
    Note over Dev,Maven: Build — runs locally or in CI
    Dev->>Maven: mvn clean package\n-pl backend/tagger -am -DskipTests
    Maven->>Maven: Compile Java source
    Maven->>Maven: Bundle Spring Boot fat JAR\n(nestor-tagger-1.0.0-SNAPSHOT.jar)
    Maven-->>Dev: ✅ target/nestor-tagger-*.jar

    %% ── Build Docker Image ─────────────────────────────────���────────
    Note over Dev,Docker: Containerise — two-stage Dockerfile
    Dev->>Docker: docker build\n--platform linux/amd64\n--provenance=false\n-t nestor-tagger .
    Docker->>Docker: Stage 1: amazoncorretto:21\nUnpack fat JAR → BOOT-INF/lib + classes
    Docker->>Docker: Stage 2: public.ecr.aws/lambda/java:21\nCopy unpacked classes + libs\nSet SPRING_CLOUD_FUNCTION_DEFINITION=taggerFunction\nCMD FunctionInvoker::handleRequest
    Docker-->>Dev: ✅ Local image nestor-tagger:latest

    %% ── Push to ECR ─────────────────────────────────────────────────
    Note over Dev,ECR: Publish
    Dev->>ECR: aws ecr get-login-password | docker login
    ECR-->>Dev: Auth token
    Dev->>Docker: docker tag nestor-tagger:latest\n<account>.dkr.ecr.<region>.amazonaws.com/nestor-tagger:latest
    Dev->>ECR: docker push nestor-tagger:latest
    ECR-->>Dev: ✅ Image digest

    %% ── Update Lambda ───────────────────────────────────────────────
    Note over Dev,Lambda: Deploy to Lambda
    Dev->>Lambda: aws lambda update-function-code\n--function-name nestor-tagger\n--image-uri <ecr-url>:latest
    Lambda->>ECR: Pull image
    ECR-->>Lambda: Image layers
    Lambda-->>Dev: ✅ Function updated (new version)

    %% ── Verify ──────────────────────────────────────────────────────
    Note over Dev,Lambda: Smoke test
    Dev->>Lambda: aws lambda invoke\n--function-name nestor-tagger\n--payload '{"instruments":[...]}'
    Lambda-->>Dev: Response payload
```

### Dockerfile Pattern (Shared by All Agents)

```dockerfile
# Stage 1: Unpack fat JAR (amazoncorretto:21)
RUN mkdir -p unpacked && cd unpacked && jar xf ../app.jar

# Stage 2: Lambda runtime (public.ecr.aws/lambda/java:21)
COPY BOOT-INF/lib/     ${LAMBDA_TASK_ROOT}/lib/
COPY BOOT-INF/classes/ ${LAMBDA_TASK_ROOT}/
COPY META-INF/         ${LAMBDA_TASK_ROOT}/META-INF/
ENV  SPRING_CLOUD_FUNCTION_DEFINITION=<beanName>
CMD  ["org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"]
```

> ⚠️ **Always use `--provenance=false`** — Docker BuildKit OCI attestation manifests are rejected by Lambda.

### Per-Module Cheat Sheet

| Module | Maven module | Spring bean | ECR repo |
|--------|-------------|------------|---------|
| API | `backend/api` | `streamLambdaHandler` | `nestor-api` |
| Planner | `backend/planner` | `plannerFunction` | `nestor-planner` |
| Tagger | `backend/tagger` | `taggerFunction` | `nestor-tagger` |
| Reporter | `backend/reporter` | `reporterFunction` | `nestor-reporter` |
| Charter | `backend/charter` | `charterFunction` | `nestor-charter` |
| Retirement | `backend/retirement` | `retirementFunction` | `nestor-retirement` |
| Ingest | `backend/ingest` | `ingestFunction` | `nestor-ingest` |
| Scheduler | `backend/scheduler` | `schedulerFunction` | `nestor-scheduler` |

---

← [06 — Authentication & API Gateway](./06_auth_and_api.md) | [Back to Index](../README.md) →

