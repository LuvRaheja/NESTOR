# NESTOR Agent Architecture

This document illustrates how the NESTOR AI agents collaborate. The agent roles and collaboration patterns are identical to Alex — the difference is the implementation technology (Java 21 + Spring Cloud Function instead of Python + OpenAI Agents SDK).

## Agent Collaboration Overview

```mermaid
graph TB
    User[User Request] -->|Portfolio Analysis| Planner[Financial Planner<br/>nestor-planner<br/>Orchestrator]
    
    Planner -->|Check Instruments| Tagger[InstrumentTagger<br/>nestor-tagger]
    Tagger -->|Classify Assets| DB[(Aurora DB)]
    
    Planner -->|Generate Analysis| Reporter[Report Writer<br/>nestor-reporter]
    Reporter -->|Markdown Reports| DB
    
    Planner -->|Create Visualizations| Charter[Chart Maker<br/>nestor-charter]
    Charter -->|JSON Chart Data| DB
    
    Planner -->|Project Future| Retirement[Retirement Specialist<br/>nestor-retirement]
    Retirement -->|Income Projections| DB
    
    DB -->|Results| Response[Complete Analysis]
    
    Planner -->|Retrieve Context| Vectors[(S3 Vectors<br/>Knowledge Base)]
    
    Schedule[EventBridge] -->|Trigger| Scheduler[nestor-research-scheduler]
    Scheduler -->|HTTP POST| Researcher[Researcher<br/>App Runner]
    Researcher -->|Store Insights| Vectors
    
    style Planner fill:#FFD700,stroke:#333,stroke-width:3px
    style Tagger fill:#98FB98
    style Reporter fill:#DDA0DD
    style Charter fill:#F0E68C
    style Retirement fill:#FFB6C1
    style Scheduler fill:#87CEEB
    style Researcher fill:#87CEEB
```

## Agent Implementation Details

### Java Implementation Pattern

Each NESTOR agent follows this pattern:

```java
@Configuration
public class TaggerConfig {
    @Bean
    public Function<Map<String, Object>, Map<String, Object>> taggerFunction(
            InstrumentClassifier classifier,
            InstrumentRepository repo) {
        return new TaggerFunction(classifier, repo);
    }
}
```

The `Function<Map, Map>` bean is discovered by Spring Cloud Function and exposed as the Lambda handler via `FunctionInvoker`.

### Agent Responsibilities

| Agent | Java Module | Spring Bean | AI Pattern | Status |
|-------|-----------|-------------|------------|--------|
| **Tagger** | `nestor-tagger` | `taggerFunction` | Bedrock Converse + toolSpec (structured output) | ✅ Deployed |
| **Planner** | `nestor-planner` | `plannerFunction` | Orchestration + Lambda invocation | ✅ Deployed |
| **Reporter** | `nestor-reporter` | `reporterFunction` | Bedrock Converse + tool calling | ✅ Deployed |
| **Charter** | `nestor-charter` | `charterFunction` | Bedrock Converse (JSON extraction) | ✅ Deployed |
| **Retirement** | `nestor-retirement` | `retirementFunction` | Monte Carlo simulation + Bedrock | ✅ Deployed |
| **Scheduler** | `nestor-scheduler` | `schedulerFunction` | HTTP POST (no AI) | ✅ Deployed |

### Key Technical Differences from Alex

| Concept | Alex (Python) | NESTOR (Java) |
|---------|--------------|---------------|
| Structured outputs | Pydantic models + `output_type` | Jackson POJOs + Bedrock `toolSpec` trick (force tool call = guaranteed JSON schema) |
| Tool calling | `@function_tool` decorator | Bedrock Converse API tool definitions |
| Agent context | `RunContextWrapper[T]` | Spring dependency injection |
| Async | `asyncio` / `await` | Synchronous (or `CompletableFuture` for fan-out) |
| Retry | `tenacity` | `Resilience4j` |
| Orchestration | `Runner.run()` with `Agent` | Direct Lambda `invoke()` via AWS SDK |

## Agent Communication Flow

```mermaid
sequenceDiagram
    participant S as EventBridge
    participant Sc as nestor-scheduler
    participant Re as Researcher (App Runner)
    participant V as S3 Vectors
    participant U as User
    participant P as nestor-planner
    participant T as nestor-tagger
    participant Rw as nestor-reporter
    participant C as nestor-charter
    participant Rt as nestor-retirement
    participant DB as Aurora DB
    
    Note over S,Re: Independent Research Flow
    S->>Sc: Trigger schedule
    Sc->>Re: HTTP POST /research
    Re->>V: Store market insights
    
    Note over U,DB: User-Triggered Analysis Flow
    U->>P: SQS → Portfolio Analysis
    P->>DB: Check missing instrument data
    
    alt Missing Instrument Data
        P->>T: Lambda invoke
        T->>DB: Upsert classifications
    end
    
    P->>V: Retrieve relevant research
    
    par Parallel Analysis
        P->>Rw: Lambda invoke
        Rw->>DB: Save report
    and
        P->>C: Lambda invoke
        C->>DB: Save charts
    and
        P->>Rt: Lambda invoke
        Rt->>DB: Save projections
    end
    
    P->>DB: Finalize results
    P->>U: Complete analysis
```

## Build & Deploy Workflow

Each agent follows this workflow:

```mermaid
graph LR
    Code[Java Source] -->|mvn package| JAR[Fat JAR]
    JAR -->|jar xf| Unpacked[Unpacked Classes + Libs]
    Unpacked -->|docker build| Image[Container Image]
    Image -->|docker push| ECR[ECR Repository]
    ECR -->|terraform| Lambda[Lambda Function]
    
    style JAR fill:#E76F00
    style Image fill:#E76F00
    style ECR fill:#FF9900
    style Lambda fill:#FF9900
```

Key commands:
```bash
mvn clean package -pl backend/<agent> -am -DskipTests
cd NESTOR/backend/<agent>
docker build --platform linux/amd64 --provenance=false -t nestor-<agent> .
docker tag nestor-<agent>:latest {account}.dkr.ecr.{region}.amazonaws.com/nestor-<agent>:latest
docker push {account}.dkr.ecr.{region}.amazonaws.com/nestor-<agent>:latest
aws lambda update-function-code --function-name nestor-<agent> --image-uri ...
```
