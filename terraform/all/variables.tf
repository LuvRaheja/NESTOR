# ========================================
# Common
# ========================================

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
}

# ========================================
# Ingestion (Part 3)
# ========================================

variable "sagemaker_endpoint_name" {
  description = "Name of the SageMaker endpoint from Part 2"
  type        = string
  default     = "alex-embedding-endpoint"
}

# ========================================
# Researcher (Part 4)
# ========================================

variable "app_runner_url" {
  description = "App Runner service URL for the Researcher agent"
  type        = string
}

variable "scheduler_enabled" {
  description = "Enable automated research scheduler via EventBridge"
  type        = bool
  default     = false
}

# ========================================
# Database (Part 5)
# ========================================

variable "min_capacity" {
  description = "Minimum capacity for Aurora Serverless v2 (in ACUs)"
  type        = number
  default     = 0.5
}

variable "max_capacity" {
  description = "Maximum capacity for Aurora Serverless v2 (in ACUs)"
  type        = number
  default     = 1.0
}

# ========================================
# Agents (Part 6)
# ========================================

variable "bedrock_model_id" {
  description = "Bedrock model ID to use for agents"
  type        = string
}

variable "bedrock_region" {
  description = "AWS region for Bedrock"
  type        = string
}

variable "sagemaker_endpoint" {
  description = "SageMaker endpoint name for embeddings (used by Reporter agent)"
  type        = string
  default     = "alex-embedding-endpoint"
}

variable "polygon_api_key" {
  description = "Polygon.io API key for market data (optional)"
  type        = string
  default     = ""
  sensitive   = true
}

# ========================================
# Frontend (Part 7)
# ========================================

variable "clerk_jwks_url" {
  description = "Clerk JWKS URL for JWT validation"
  type        = string
}

variable "clerk_issuer" {
  description = "Clerk issuer URL"
  type        = string
  default     = ""
}
