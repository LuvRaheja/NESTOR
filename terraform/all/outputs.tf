# ========================================
# Ingestion (Part 3)
# ========================================

output "vector_bucket_name" {
  description = "Name of the S3 Vectors bucket"
  value       = module.ingestion.vector_bucket_name
}

output "ingest_ecr_repository_url" {
  description = "ECR repository URL for the ingest container image"
  value       = module.ingestion.ecr_repository_url
}

output "ingest_api_endpoint" {
  description = "API Gateway endpoint URL for ingestion"
  value       = module.ingestion.api_endpoint
}

output "ingest_api_key_id" {
  description = "API Key ID for ingestion"
  value       = module.ingestion.api_key_id
}

output "ingest_api_key_value" {
  description = "API Key value for ingestion (sensitive)"
  value       = module.ingestion.api_key_value
  sensitive   = true
}

# ========================================
# Researcher (Part 4)
# ========================================

output "scheduler_ecr_repository_url" {
  description = "ECR repository URL for the scheduler container image"
  value       = module.researcher.ecr_repository_url
}

output "scheduler_lambda_function_name" {
  description = "Name of the scheduler Lambda function"
  value       = module.researcher.lambda_function_name
}

# ========================================
# Database (Part 5)
# ========================================

output "aurora_cluster_arn" {
  description = "ARN of the Aurora cluster"
  value       = module.database.aurora_cluster_arn
}

output "aurora_cluster_endpoint" {
  description = "Writer endpoint for the Aurora cluster"
  value       = module.database.aurora_cluster_endpoint
}

output "aurora_secret_arn" {
  description = "ARN of the Secrets Manager secret containing database credentials"
  value       = module.database.aurora_secret_arn
}

output "database_name" {
  description = "Name of the database"
  value       = module.database.database_name
}

# ========================================
# Agents (Part 6)
# ========================================

output "tagger_ecr_repository_url" {
  description = "ECR repository URL for the tagger container image"
  value       = module.agents.ecr_repository_url
}

output "charter_ecr_repository_url" {
  description = "ECR repository URL for the charter container image"
  value       = module.agents.ecr_charter_repository_url
}

output "retirement_ecr_repository_url" {
  description = "ECR repository URL for the retirement container image"
  value       = module.agents.ecr_retirement_repository_url
}

output "reporter_ecr_repository_url" {
  description = "ECR repository URL for the reporter container image"
  value       = module.agents.ecr_reporter_repository_url
}

output "planner_ecr_repository_url" {
  description = "ECR repository URL for the planner container image"
  value       = module.agents.ecr_planner_repository_url
}

output "sqs_queue_url" {
  description = "SQS queue URL for analysis jobs"
  value       = module.agents.sqs_queue_url
}

output "sqs_queue_arn" {
  description = "SQS queue ARN for analysis jobs"
  value       = module.agents.sqs_queue_arn
}

output "lambda_functions" {
  description = "Names of deployed agent Lambda functions"
  value       = module.agents.lambda_functions
}

# ========================================
# Frontend (Part 7)
# ========================================

output "api_ecr_repository_url" {
  description = "ECR repository URL for the API container image"
  value       = module.frontend.ecr_api_repository_url
}

output "api_gateway_url" {
  description = "API Gateway endpoint URL"
  value       = module.frontend.api_gateway_url
}

output "cloudfront_url" {
  description = "CloudFront distribution URL"
  value       = module.frontend.cloudfront_url
}

output "frontend_s3_bucket_name" {
  description = "Name of the S3 bucket for frontend static files"
  value       = module.frontend.s3_bucket_name
}
