# Graph Report - backend  (2026-04-18)

## Corpus Check
- Corpus is ~34,343 words - fits in a single context window. You may not need a graph.

## Summary
- 716 nodes · 1394 edges · 45 communities detected
- Extraction: 64% EXTRACTED · 36% INFERRED · 0% AMBIGUOUS · INFERRED: 504 edges (avg confidence: 0.64)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]

## God Nodes (most connected - your core abstractions)
1. `DataAPIClient` - 51 edges
2. `InstrumentCreate` - 45 edges
3. `UserCreate` - 43 edges
4. `AccountCreate` - 43 edges
5. `PositionCreate` - 43 edges
6. `JobCreate` - 39 edges
7. `JobUpdate` - 39 edges
8. `Database` - 21 edges
9. `BaseModel` - 20 edges
10. `InstrumentClassification` - 19 edges

## Surprising Connections (you probably didn't know these)
- `Insert a single instrument into the database with Pydantic validation` --uses--> `InstrumentCreate`  [INFERRED]
  backend\database\seed_data.py → backend\database\src\schemas.py
- `Verify instrument using Pydantic validation` --uses--> `InstrumentCreate`  [INFERRED]
  backend\database\seed_data.py → backend\database\src\schemas.py
- `test_multiple_accounts()` --calls--> `Database`  [INFERRED]
  backend\test_multiple_accounts.py → backend\database\src\models.py
- `create_test_user()` --calls--> `Database`  [INFERRED]
  backend\test_scale.py → backend\database\src\models.py
- `monitor_job()` --calls--> `Database`  [INFERRED]
  backend\test_scale.py → backend\database\src\models.py

## Hyperedges (group relationships)
- **** — planner_agent, tagger_agent, reporter_agent, charter_agent, retirement_agent [EXTRACTED]
- **** — account_controller, analysis_controller, health_controller, instrument_controller, position_controller, test_data_controller, user_controller [EXTRACTED]
- **** — user_repository, account_repository, position_repository, job_repository, instrument_repository [EXTRACTED]
- **** — reporter_lambda, charter_lambda, retirement_lambda, tagger_lambda [EXTRACTED]
- **** — users_table, accounts_table, positions_table, instruments_table, jobs_table [EXTRACTED]
- **** — account_repository, instrument_repository, job_repository, position_repository, user_repository [EXTRACTED]
- **** — planner_function, agent_orchestrator, lambda_agent_invoker, portfolio_loader, market_price_updater [EXTRACTED]
- **** — instrument_create_schema, user_create_schema, account_create_schema, position_create_schema, job_create_schema [EXTRACTED]
- **** — reporter_function, report_generator, report_judge [EXTRACTED]
- **** — reporter_service, retirement_service, tagger_service [EXTRACTED]
- **** — monte_carlo_simulator, india_tax_calculator, portfolio_calculator [EXTRACTED]
- **** — report_generator, retirement_analyzer, instrument_classifier, report_judge [EXTRACTED]
- **** — reporter_config, retirement_config, tagger_config, scheduler_config [EXTRACTED]
- **** — reporter_templates, retirement_templates, tagger_templates, planner_templates [EXTRACTED]
- **** — planner_lambda, reporter_lambda, charter_lambda, retirement_lambda, tagger_lambda [EXTRACTED]
- **** — api_lambda, planner_lambda, tagger_lambda, reporter_lambda, charter_lambda, retirement_lambda, ingest_lambda, scheduler_lambda [EXTRACTED]
- **** — api_lambda, planner_lambda, tagger_lambda, reporter_lambda, charter_lambda, retirement_lambda [EXTRACTED]

## Communities

### Community 0 - "Community 0"
Cohesion: 0.0
Nodes (78): BaseModel, DataAPIClient, Aurora Data API Client Wrapper Provides a simple interface for database operati, Execute a SELECT query and return first result          Args:             sql, Begin a database transaction, Commit a database transaction, Rollback a database transaction, Wrapper for AWS RDS Data API to simplify database operations (+70 more)

### Community 1 - "Community 1"
Cohesion: 0.0
Nodes (95): AccountController, AccountRepository (Common), accounts Table, AgentOrchestrator, AnalysisController, API Gateway, nestor-api (REST API Lambda), AppConfig (+87 more)

### Community 2 - "Community 2"
Cohesion: 0.0
Nodes (12): AccountController, AnalysisController, DataApiClient, InstrumentController, InstrumentRepository, JobRepository, PlannerFunction, PortfolioLoader (+4 more)

### Community 3 - "Community 3"
Cohesion: 0.0
Nodes (19): AccountRepository, Insert a record into a table          Args:             table: Table name, Update records in a table          Args:             table: Table name, Delete records from a table          Args:             table: Table name, Convert dictionary to Data API parameter format, Extract value from Data API field response, Execute a SQL statement          Args:             sql: SQL statement to exec, SchedulerFunction (+11 more)

### Community 4 - "Community 4"
Cohesion: 0.0
Nodes (7): MarketPriceUpdater, PlannerTemplates, AccountTotal, PortfolioAnalyzer, PortfolioFormatter, ReporterFunction, ReportJudge

### Community 5 - "Community 5"
Cohesion: 0.0
Nodes (3): InstrumentClassification, InstrumentClassifier, TaggerFunction

### Community 6 - "Community 6"
Cohesion: 0.0
Nodes (4): IndiaTaxCalculator, MonteCarloSimulator, PortfolioCalculator, RetirementFunction

### Community 7 - "Community 7"
Cohesion: 0.0
Nodes (6): BedrockConverse, CharterFunction, CharterTemplates, ChartGenerator, ReportGenerator, RetirementAnalyzer

### Community 8 - "Community 8"
Cohesion: 0.0
Nodes (24): AccountCreate Schema, Accounts Model (Python), Aurora Serverless v2 (PostgreSQL), BaseModel (Python), DataAPIClient (Python), Database Facade, Database Module (Python), India Localization Support (+16 more)

### Community 9 - "Community 9"
Cohesion: 0.0
Nodes (2): AgentOrchestrator, LambdaAgentInvoker

### Community 10 - "Community 10"
Cohesion: 0.0
Nodes (3): IngestFunction, MarketInsightsClient, S3VectorsClient

### Community 11 - "Community 11"
Cohesion: 0.0
Nodes (16): AgentLogWatcher, AWS CloudWatch Logs, AWS SQS (alex-analysis-jobs), Charter Agent Lambda, check_db.py, check_job_details.py, Planner Agent Lambda, Python Database Client (+8 more)

### Community 12 - "Community 12"
Cohesion: 0.0
Nodes (1): PlannerConfig

### Community 13 - "Community 13"
Cohesion: 0.0
Nodes (8): AgentLogWatcher, main(), Format a log message with color coding., Poll a single agent for new log events., Watch all agent logs continuously., Watches CloudWatch logs for all agents., Initialize the log watcher., Get log events for a specific agent.

### Community 14 - "Community 14"
Cohesion: 0.0
Nodes (4): drop_all_tables(), main(), Drop all tables in correct order (respecting foreign keys), TaggerConfig

### Community 15 - "Community 15"
Cohesion: 0.0
Nodes (1): AppConfig

### Community 16 - "Community 16"
Cohesion: 0.0
Nodes (1): ReporterConfig

### Community 17 - "Community 17"
Cohesion: 0.0
Nodes (1): CharterConfig

### Community 18 - "Community 18"
Cohesion: 0.0
Nodes (7): get_cluster_details(), get_current_region(), main(), Test the Data API connection, Get the current AWS region from the session, Get Aurora cluster ARN and secret ARN from environment variables or verify they, test_data_api()

### Community 19 - "Community 19"
Cohesion: 0.0
Nodes (1): RetirementConfig

### Community 20 - "Community 20"
Cohesion: 0.0
Nodes (5): main(), Run a command and capture output., Test an individual agent in its directory., run_command(), test_agent()

### Community 21 - "Community 21"
Cohesion: 0.0
Nodes (5): insert_instrument(), main(), Insert a single instrument into the database with Pydantic validation, Verify instrument using Pydantic validation, verify_allocations()

### Community 22 - "Community 22"
Cohesion: 0.0
Nodes (1): IngestConfig

### Community 23 - "Community 23"
Cohesion: 0.0
Nodes (1): EmbeddingClient

### Community 24 - "Community 24"
Cohesion: 0.0
Nodes (1): SecurityConfig

### Community 25 - "Community 25"
Cohesion: 0.0
Nodes (1): GlobalExceptionHandler

### Community 26 - "Community 26"
Cohesion: 0.0
Nodes (3): execute_query(), main(), Execute a query and return results

### Community 27 - "Community 27"
Cohesion: 0.0
Nodes (1): SchedulerConfig

### Community 28 - "Community 28"
Cohesion: 0.0
Nodes (4): ApiApplication, API Gateway HTTP API v2, GlobalExceptionHandler, StreamLambdaHandler

### Community 29 - "Community 29"
Cohesion: 0.0
Nodes (4): Planner Service (Orchestrator), PlannerTemplates, Reporter Service, Retirement Service

### Community 30 - "Community 30"
Cohesion: 0.0
Nodes (4): App Runner Researcher Service, AWS EventBridge, SchedulerConfig, SchedulerFunction

### Community 31 - "Community 31"
Cohesion: 0.0
Nodes (1): ApiApplication

### Community 32 - "Community 32"
Cohesion: 0.0
Nodes (1): StreamLambdaHandler

### Community 33 - "Community 33"
Cohesion: 0.0
Nodes (1): HealthController

### Community 34 - "Community 34"
Cohesion: 0.0
Nodes (1): ReporterTemplates

### Community 35 - "Community 35"
Cohesion: 0.0
Nodes (1): RetirementTemplates

### Community 36 - "Community 36"
Cohesion: 0.0
Nodes (1): TaggerTemplates

### Community 37 - "Community 37"
Cohesion: 0.0
Nodes (1): Update region_targets for Indian users from north_america to india.

### Community 38 - "Community 38"
Cohesion: 0.0
Nodes (0): 

### Community 39 - "Community 39"
Cohesion: 0.0
Nodes (0): 

### Community 40 - "Community 40"
Cohesion: 0.0
Nodes (0): 

### Community 41 - "Community 41"
Cohesion: 0.0
Nodes (1): Ensure allocation percentages sum to 100

### Community 42 - "Community 42"
Cohesion: 0.0
Nodes (1): Ensure all allocations sum to 100

### Community 43 - "Community 43"
Cohesion: 0.0
Nodes (1): HealthController

### Community 44 - "Community 44"
Cohesion: 0.0
Nodes (1): Scheduler Service

## Knowledge Gaps
- **48 isolated node(s):** `Test analysis for a user with multiple accounts`, `Create a test user with specified number of accounts and positions`, `Monitor a single job until completion`, `Run the scale test with multiple users`, `Run a command and capture output.` (+43 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 37`** (2 nodes): `update_region_targets.py`, `Update region_targets for Indian users from north_america to india.`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 38`** (1 nodes): `check_db.py`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (1 nodes): `check_job_details.py`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 40`** (1 nodes): `run_migrations.py`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 41`** (1 nodes): `Ensure allocation percentages sum to 100`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (1 nodes): `Ensure all allocations sum to 100`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (1 nodes): `HealthController`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (1 nodes): `Scheduler Service`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.