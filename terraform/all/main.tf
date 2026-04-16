terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# ========================================
# Module: Ingestion Pipeline
# S3 Vectors bucket, Ingest Lambda, API Gateway
# ========================================

module "ingestion" {
  source = "../3_ingestion"

  aws_region              = var.aws_region
  sagemaker_endpoint_name = var.sagemaker_endpoint_name
}

# ========================================
# Module: Researcher Scheduler
# Scheduler Lambda + optional EventBridge
# ========================================

module "researcher" {
  source = "../4_researcher"

  aws_region        = var.aws_region
  app_runner_url    = var.app_runner_url
  scheduler_enabled = var.scheduler_enabled
}

# ========================================
# Module: Database
# Aurora Serverless v2 PostgreSQL
# ========================================

module "database" {
  source = "../5_database"

  aws_region   = var.aws_region
  min_capacity = var.min_capacity
  max_capacity = var.max_capacity
}

# ========================================
# Module: AI Agents
# 5 Lambda functions (Planner, Tagger, Reporter, Charter, Retirement) + SQS
# ========================================

module "agents" {
  source = "../6_agents"

  aws_region         = var.aws_region
  aurora_cluster_arn = module.database.aurora_cluster_arn
  aurora_secret_arn  = module.database.aurora_secret_arn
  bedrock_model_id   = var.bedrock_model_id
  bedrock_region     = var.bedrock_region
  sagemaker_endpoint = var.sagemaker_endpoint
  polygon_api_key    = var.polygon_api_key
}

# ========================================
# Module: Frontend
# API Lambda, CloudFront CDN, S3, API Gateway HTTP
# ========================================

module "frontend" {
  source = "../7_frontend"

  aws_region         = var.aws_region
  aurora_cluster_arn = module.database.aurora_cluster_arn
  aurora_secret_arn  = module.database.aurora_secret_arn
  sqs_queue_url      = module.agents.sqs_queue_url
  sqs_queue_arn      = module.agents.sqs_queue_arn
  clerk_jwks_url     = var.clerk_jwks_url
  clerk_issuer       = var.clerk_issuer
}
