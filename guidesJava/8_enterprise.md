# Building NESTOR: Part 8 - Enterprise Grade

Enterprise features (scalability, security, monitoring, guardrails, observability) are shared infrastructure concerns that apply to both Alex and NESTOR. This guide covers what's the same and what differs for Java Lambdas.

## Scalability

The serverless architecture scales identically for Java and Python:

- **Lambda** scales automatically (default 1,000 concurrent, tunable)
- **Aurora Serverless v2** scales from 0.5 to 128 ACUs
- **API Gateway** handles millions of requests/second
- **SQS** provides unlimited throughput

### Java-Specific Considerations

**Cold Starts**: Java Lambda cold starts are longer than Python (~5-15 seconds vs ~1-2 seconds). Mitigations:
- Use `JAVA_TOOL_OPTIONS=-XX:+TieredCompilation -XX:TieredStopAtLevel=1` (set in Terraform env vars)
- Consider **Provisioned Concurrency** for latency-critical functions (Planner, API)
- Future: GraalVM native-image build for ~100-300ms cold starts

**Memory**: Java Lambdas generally need more memory. Recommended:
- Tagger: 1024 MB
- Reporter/Charter/Retirement: 1024 MB
- Planner: 2048 MB
- API: 1024 MB

## Security

Same security model as Alex:
- **IAM Roles**: Least privilege per Lambda
- **Clerk JWT**: Authentication for API
- **Secrets Manager**: Database credentials
- **ECR**: Image scanning enabled on push (additional for NESTOR)

### Container Security

NESTOR adds container-specific security:
- ECR image scanning detects vulnerabilities in dependencies
- Base image `public.ecr.aws/lambda/java:21` is AWS-maintained and patched
- Use `force_delete = true` on ECR repos only in dev (remove for production)

## Monitoring

### CloudWatch Dashboards

Deploy monitoring infrastructure:
```bash
# Use the SAME terraform directory — shared infrastructure
cd terraform/8_enterprise
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform apply
```

### NESTOR-Specific Log Groups

| Lambda | Log Group |
|--------|-----------|
| `nestor-tagger` | `/aws/lambda/nestor-tagger` |
| `nestor-research-scheduler` | `/aws/lambda/nestor-research-scheduler` |
| `nestor-planner` (planned) | `/aws/lambda/nestor-planner` |
| `nestor-reporter` (planned) | `/aws/lambda/nestor-reporter` |
| `nestor-charter` (planned) | `/aws/lambda/nestor-charter` |
| `nestor-retirement` (planned) | `/aws/lambda/nestor-retirement` |

View logs:
```bash
aws logs tail /aws/lambda/nestor-tagger --follow --region us-east-1
```

## Observability

### LangFuse Integration

NESTOR can integrate with LangFuse for agent tracing. Since the Java agents call Bedrock directly (not via LiteLLM), observability is implemented via:
- SLF4J structured logging with Spring Boot
- Custom `ObservabilityConfig` in `nestor-common`
- Optional LangFuse Java SDK integration

### Application Logging

NESTOR uses SLF4J + Logback (included with Spring Boot):
```java
private static final Logger log = LoggerFactory.getLogger(TaggerFunction.class);
log.info("Processing {} instruments", instruments.size());
```

Logs are automatically captured by CloudWatch.

## Guardrails

### Input Validation

NESTOR validates inputs at the Lambda boundary using Jackson deserialization. Invalid JSON or missing fields result in clear error responses.

### Output Validation

- **Tagger**: Validates allocation percentages sum to ~100 (±3 tolerance)
- **Reporter**: Quality judge evaluator (planned)
- **Charter**: Chart JSON schema validation
- **Retirement**: Monte Carlo simulation parameter bounds

### Bedrock Rate Limiting

NESTOR uses Resilience4j for retry with exponential backoff on Bedrock rate limits:
```java
RetryConfig config = RetryConfig.custom()
    .maxAttempts(5)
    .waitDuration(Duration.ofSeconds(2))
    .retryExceptions(ThrottlingException.class)
    .build();
```

## Cost Management

### Cleanup

Destroy in reverse order:
```bash
# NESTOR-specific infra
cd NESTOR/terraform/6_agents && terraform destroy
cd NESTOR/terraform/4_researcher && terraform destroy

# Shared infra (also removes Alex)
cd terraform/8_enterprise && terraform destroy
cd terraform/7_frontend && terraform destroy
cd terraform/6_agents && terraform destroy
cd terraform/5_database && terraform destroy  # Biggest cost savings
cd terraform/4_researcher && terraform destroy
cd terraform/3_ingestion && terraform destroy
cd terraform/2_sagemaker && terraform destroy
```

### ECR Cleanup

ECR images have lifecycle policies (keep last 5), but you can also manually clean up:
```bash
aws ecr delete-repository --repository-name nestor-tagger --force
aws ecr delete-repository --repository-name nestor-scheduler --force
```
