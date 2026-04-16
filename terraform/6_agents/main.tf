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

provider "aws" {
  region = var.aws_region
}

# Data source for current caller identity
data "aws_caller_identity" "current" {}

# ========================================
# ECR Repository for Tagger Container Image
# ========================================

# Resolve the actual image digest so Terraform detects new pushes
data "aws_ecr_image" "tagger_latest" {
  repository_name = aws_ecr_repository.tagger.name
  image_tag       = "latest"
}


resource "aws_ecr_repository" "tagger" {
  name                 = "nestor-tagger"
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "tagger"
  }
}

# Lifecycle policy to keep only the last 5 images
resource "aws_ecr_lifecycle_policy" "tagger" {
  repository = aws_ecr_repository.tagger.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only last 5 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ========================================
# IAM Role for Lambda Functions
# ========================================

resource "aws_iam_role" "lambda_agents_role" {
  name = "nestor-lambda-agents-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Project = "nestor"
    Part    = "6"
  }
}

# IAM policy for Lambda agents
resource "aws_iam_role_policy" "lambda_agents_policy" {
  name = "nestor-lambda-agents-policy"
  role = aws_iam_role.lambda_agents_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # CloudWatch Logs
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:*"
      },
      # Lambda invocation for orchestrator to call other agents
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = "arn:aws:lambda:${var.aws_region}:${data.aws_caller_identity.current.account_id}:function:nestor-*"
      },
      # Aurora Data API access
      {
        Effect = "Allow"
        Action = [
          "rds-data:ExecuteStatement",
          "rds-data:BatchExecuteStatement",
          "rds-data:BeginTransaction",
          "rds-data:CommitTransaction",
          "rds-data:RollbackTransaction"
        ]
        Resource = var.aurora_cluster_arn
      },
      # Secrets Manager for database credentials
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = var.aurora_secret_arn
      },
      # ECR pull permissions for container images
      {
        Effect = "Allow"
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      # Bedrock access for all agents
      {
        Effect = "Allow"
        Action = [
          "bedrock:InvokeModel",
          "bedrock:InvokeModelWithResponseStream"
        ]
        Resource = [
          "arn:aws:bedrock:*::foundation-model/*",
          "arn:aws:bedrock:*:*:inference-profile/*"
        ]
      },
      # SageMaker endpoint for embeddings (Reporter)
      {
        Effect = "Allow"
        Action = [
          "sagemaker:InvokeEndpoint"
        ]
        Resource = "arn:aws:sagemaker:${var.aws_region}:${data.aws_caller_identity.current.account_id}:endpoint/*"
      },
      # STS for account ID lookup (Reporter)
      {
        Effect = "Allow"
        Action = [
          "sts:GetCallerIdentity"
        ]
        Resource = "*"
      },
      # S3 Vectors for market insights (Reporter)
      {
        Effect = "Allow"
        Action = [
          "s3vectors:QueryVectors",
          "s3vectors:GetVectors",
          "s3vectors:ListVectorBuckets",
          "s3vectors:ListVectorIndexes"
        ]
        Resource = "*"
      }
    ]
  })
}

# Attach basic Lambda execution role
resource "aws_iam_role_policy_attachment" "lambda_agents_basic" {
  role       = aws_iam_role.lambda_agents_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# ========================================
# Tagger Lambda Function (Container Image)
# ========================================

resource "aws_lambda_function" "tagger" {
  function_name = "nestor-tagger"
  role          = aws_iam_role.lambda_agents_role.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.tagger.repository_url}:latest"

  timeout     = 300 # 5 minutes for tagger
  memory_size = 1024

  environment {
    variables = {
      AURORA_CLUSTER_ARN                  = var.aurora_cluster_arn
      AURORA_SECRET_ARN                   = var.aurora_secret_arn
      DATABASE_NAME                       = "alex"
      BEDROCK_MODEL_ID                    = var.bedrock_model_id
      BEDROCK_REGION                      = var.bedrock_region
      DEFAULT_AWS_REGION                  = var.aws_region
      SPRING_CLOUD_FUNCTION_DEFINITION    = "taggerFunction"
      WEB_TYPE                            = "none"
    }
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "tagger"
  }
}

# ========================================
# CloudWatch Log Group
# ========================================

resource "aws_cloudwatch_log_group" "tagger_logs" {
  name              = "/aws/lambda/nestor-tagger"
  retention_in_days = 7

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "tagger"
  }
}

# ========================================
# ECR Repository for Charter Container Image
# ========================================

resource "aws_ecr_repository" "charter" {
  name                 = "nestor-charter"
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "charter"
  }
}

# Lifecycle policy to keep only the last 5 images
resource "aws_ecr_lifecycle_policy" "charter" {
  repository = aws_ecr_repository.charter.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only last 5 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ========================================
# Charter Lambda Function (Container Image)
# ========================================

resource "aws_lambda_function" "charter" {
  function_name = "nestor-charter"
  role          = aws_iam_role.lambda_agents_role.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.charter.repository_url}:latest"

  timeout     = 300 # 5 minutes
  memory_size = 1024

  environment {
    variables = {
      AURORA_CLUSTER_ARN                  = var.aurora_cluster_arn
      AURORA_SECRET_ARN                   = var.aurora_secret_arn
      DATABASE_NAME                       = "alex"
      BEDROCK_MODEL_ID                    = var.bedrock_model_id
      BEDROCK_REGION                      = var.bedrock_region
      DEFAULT_AWS_REGION                  = var.aws_region
      SPRING_CLOUD_FUNCTION_DEFINITION    = "charterFunction"
    }
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "charter"
  }
}

# ========================================
# CloudWatch Log Group for Charter
# ========================================

resource "aws_cloudwatch_log_group" "charter_logs" {
  name              = "/aws/lambda/nestor-charter"
  retention_in_days = 7

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "charter"
  }
}

# ========================================
# ECR Repository for Retirement Container Image
# ========================================

resource "aws_ecr_repository" "retirement" {
  name                 = "nestor-retirement"
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "retirement"
  }
}

# Lifecycle policy to keep only the last 5 images
resource "aws_ecr_lifecycle_policy" "retirement" {
  repository = aws_ecr_repository.retirement.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only last 5 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ========================================
# Retirement Lambda Function (Container Image)
# ========================================

resource "aws_lambda_function" "retirement" {
  function_name = "nestor-retirement"
  role          = aws_iam_role.lambda_agents_role.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.retirement.repository_url}:latest"

  timeout     = 300 # 5 minutes
  memory_size = 1024

  environment {
    variables = {
      AURORA_CLUSTER_ARN                  = var.aurora_cluster_arn
      AURORA_SECRET_ARN                   = var.aurora_secret_arn
      DATABASE_NAME                       = "alex"
      BEDROCK_MODEL_ID                    = var.bedrock_model_id
      BEDROCK_REGION                      = var.bedrock_region
      DEFAULT_AWS_REGION                  = var.aws_region
      SPRING_CLOUD_FUNCTION_DEFINITION    = "retirementFunction"
    }
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "retirement"
  }
}

# ========================================
# CloudWatch Log Group for Retirement
# ========================================

resource "aws_cloudwatch_log_group" "retirement_logs" {
  name              = "/aws/lambda/nestor-retirement"
  retention_in_days = 7

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "retirement"
  }
}

# ========================================
# ECR Repository for Reporter Container Image
# ========================================

resource "aws_ecr_repository" "reporter" {
  name                 = "nestor-reporter"
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "reporter"
  }
}

# Lifecycle policy to keep only the last 5 images
resource "aws_ecr_lifecycle_policy" "reporter" {
  repository = aws_ecr_repository.reporter.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only last 5 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ========================================
# Reporter Lambda Function (Container Image)
# ========================================

resource "aws_lambda_function" "reporter" {
  function_name = "nestor-reporter"
  role          = aws_iam_role.lambda_agents_role.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.reporter.repository_url}:latest"

  timeout     = 300 # 5 minutes
  memory_size = 1024

  environment {
    variables = {
      AURORA_CLUSTER_ARN                  = var.aurora_cluster_arn
      AURORA_SECRET_ARN                   = var.aurora_secret_arn
      DATABASE_NAME                       = "alex"
      BEDROCK_MODEL_ID                    = var.bedrock_model_id
      BEDROCK_REGION                      = var.bedrock_region
      DEFAULT_AWS_REGION                  = var.aws_region
      SAGEMAKER_ENDPOINT                  = var.sagemaker_endpoint
      SPRING_CLOUD_FUNCTION_DEFINITION    = "reporterFunction"
    }
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "reporter"
  }
}

# ========================================
# CloudWatch Log Group for Reporter
# ========================================

resource "aws_cloudwatch_log_group" "reporter_logs" {
  name              = "/aws/lambda/nestor-reporter"
  retention_in_days = 7

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "reporter"
  }
}

# ========================================
# ECR Repository for Planner Container Image
# ========================================

resource "aws_ecr_repository" "planner" {
  name                 = "nestor-planner"
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "planner"
  }
}

# Lifecycle policy to keep only the last 5 images
resource "aws_ecr_lifecycle_policy" "planner" {
  repository = aws_ecr_repository.planner.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only last 5 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ========================================
# Planner Lambda Function (Container Image)
# ========================================

resource "aws_lambda_function" "planner" {
  function_name = "nestor-planner"
  role          = aws_iam_role.lambda_agents_role.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.planner.repository_url}:latest"

  timeout     = 900 # 15 minutes (orchestrator needs time for child agents)
  memory_size = 2048

  environment {
    variables = {
      AURORA_CLUSTER_ARN                  = var.aurora_cluster_arn
      AURORA_SECRET_ARN                   = var.aurora_secret_arn
      DATABASE_NAME                       = "alex"
      BEDROCK_MODEL_ID                    = var.bedrock_model_id
      BEDROCK_REGION                      = var.bedrock_region
      DEFAULT_AWS_REGION                  = var.aws_region
      TAGGER_FUNCTION                     = aws_lambda_function.tagger.function_name
      REPORTER_FUNCTION                   = aws_lambda_function.reporter.function_name
      CHARTER_FUNCTION                    = aws_lambda_function.charter.function_name
      RETIREMENT_FUNCTION                 = aws_lambda_function.retirement.function_name
      POLYGON_API_KEY                     = var.polygon_api_key
      SPRING_CLOUD_FUNCTION_DEFINITION    = "plannerFunction"
      JAVA_TOOL_OPTIONS                   = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    }
  }

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "planner"
  }
}

# ========================================
# CloudWatch Log Group for Planner
# ========================================

resource "aws_cloudwatch_log_group" "planner_logs" {
  name              = "/aws/lambda/nestor-planner"
  retention_in_days = 7

  tags = {
    Project = "nestor"
    Part    = "6"
    Agent   = "planner"
  }
}

# ========================================
# SQS Queue for Analysis Jobs
# ========================================

resource "aws_sqs_queue" "analysis_jobs" {
  name                       = "nestor-analysis-jobs"
  visibility_timeout_seconds = 960 # Slightly more than Lambda timeout (900s)
  message_retention_seconds  = 86400
  receive_wait_time_seconds  = 10

  tags = {
    Project = "nestor"
    Part    = "6"
  }
}

# ========================================
# SQS Event Source Mapping for Planner
# ========================================

resource "aws_lambda_event_source_mapping" "planner_sqs" {
  event_source_arn = aws_sqs_queue.analysis_jobs.arn
  function_name    = aws_lambda_function.planner.arn
  batch_size       = 1
  enabled          = true
}

# Allow SQS to invoke Planner Lambda
resource "aws_iam_role_policy" "planner_sqs_policy" {
  name = "nestor-planner-sqs-policy"
  role = aws_iam_role.lambda_agents_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.analysis_jobs.arn
      }
    ]
  })
}
