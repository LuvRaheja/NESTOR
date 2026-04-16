terraform {
  required_version = ">= 1.5"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  # Using local backend - state will be stored in terraform.tfstate in this directory
  # This is automatically gitignored for security
}

# ========================================
# Read .env file from NESTOR project root
# ========================================
locals {
  env_file_content = file("${path.module}/../../.env")
  env_lines        = [for line in split("\n", local.env_file_content) : trimspace(line)]
  env_map = {
    for line in local.env_lines :
    trimspace(split("=", line)[0]) => trimspace(join("=", slice(split("=", line), 1, length(split("=", line)))))
    if length(line) > 0 && !startswith(line, "#") && strcontains(line, "=")
  }

  aws_region = local.env_map["DEFAULT_AWS_REGION"]
}

provider "aws" {
  region = local.aws_region
}

# Data source for current caller identity
data "aws_caller_identity" "current" {}

# IAM role for SageMaker
resource "aws_iam_role" "sagemaker_role" {
  name = "alex-sagemaker-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "sagemaker.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sagemaker_full_access" {
  role       = aws_iam_role.sagemaker_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSageMakerFullAccess"
}

# SageMaker Model
resource "aws_sagemaker_model" "embedding_model" {
  name               = "alex-embedding-model"
  execution_role_arn = aws_iam_role.sagemaker_role.arn

  primary_container {
    image = var.sagemaker_image_uri
    environment = {
      HF_MODEL_ID = var.embedding_model_name
      HF_TASK     = "feature-extraction"
    }
  }

  depends_on = [aws_iam_role_policy_attachment.sagemaker_full_access]
}

# Serverless Inference Config
resource "aws_sagemaker_endpoint_configuration" "serverless_config" {
  name = "alex-embedding-serverless-config"

  production_variants {
    model_name = aws_sagemaker_model.embedding_model.name
    
    serverless_config {
      memory_size_in_mb = 3072
      max_concurrency   = 2
    }
  }
}

# Add a delay for IAM role propagation before creating endpoint
resource "time_sleep" "wait_for_iam_propagation" {
  depends_on = [
    aws_iam_role_policy_attachment.sagemaker_full_access
  ]
  
  create_duration = "15s"
}

# SageMaker Endpoint
resource "aws_sagemaker_endpoint" "embedding_endpoint" {
  name                 = "alex-embedding-endpoint"
  endpoint_config_name = aws_sagemaker_endpoint_configuration.serverless_config.name
  
  depends_on = [
    time_sleep.wait_for_iam_propagation
  ]
  
}
