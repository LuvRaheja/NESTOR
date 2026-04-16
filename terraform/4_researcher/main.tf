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

data "aws_caller_identity" "current" {}

# ========================================
# ECR Repository for Scheduler Container Image
# ========================================

resource "aws_ecr_repository" "scheduler" {
  name                 = "nestor-scheduler"
  image_tag_mutability = "MUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Project = "nestor"
    Part    = "4"
    Agent   = "scheduler"
  }
}

resource "aws_ecr_lifecycle_policy" "scheduler" {
  repository = aws_ecr_repository.scheduler.name

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
# IAM Role for Scheduler Lambda
# ========================================

resource "aws_iam_role" "lambda_scheduler_role" {
  name = "nestor-scheduler-lambda-role"

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
    Part    = "4"
  }
}

resource "aws_iam_role_policy" "lambda_scheduler_policy" {
  name = "nestor-scheduler-lambda-policy"
  role = aws_iam_role.lambda_scheduler_role.id

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
      # ECR pull permissions for container images
      {
        Effect = "Allow"
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_scheduler_basic" {
  role       = aws_iam_role.lambda_scheduler_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# ========================================
# Scheduler Lambda Function (Container Image)
# ========================================

resource "aws_lambda_function" "scheduler" {
  function_name = "nestor-research-scheduler"
  role          = aws_iam_role.lambda_scheduler_role.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.scheduler.repository_url}:latest"

  timeout     = 180 # 3 minutes to handle App Runner response time
  memory_size = 256

  environment {
    variables = {
      APP_RUNNER_URL                      = var.app_runner_url
      SPRING_CLOUD_FUNCTION_DEFINITION    = "schedulerFunction"
    }
  }

  tags = {
    Project = "nestor"
    Part    = "4"
    Agent   = "scheduler"
  }
}

# ========================================
# EventBridge Scheduler (Optional)
# ========================================

resource "aws_iam_role" "eventbridge_role" {
  count = var.scheduler_enabled ? 1 : 0
  name  = "nestor-eventbridge-scheduler-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "scheduler.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Project = "nestor"
    Part    = "4"
  }
}

resource "aws_scheduler_schedule" "research_schedule" {
  count = var.scheduler_enabled ? 1 : 0
  name  = "nestor-research-schedule"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "rate(2 hours)"

  target {
    arn      = aws_lambda_function.scheduler.arn
    role_arn = aws_iam_role.eventbridge_role[0].arn
  }
}

resource "aws_lambda_permission" "allow_eventbridge" {
  count         = var.scheduler_enabled ? 1 : 0
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.scheduler.function_name
  principal     = "scheduler.amazonaws.com"
  source_arn    = aws_scheduler_schedule.research_schedule[0].arn
}

resource "aws_iam_role_policy" "eventbridge_invoke_lambda" {
  count = var.scheduler_enabled ? 1 : 0
  name  = "InvokeLambdaPolicy"
  role  = aws_iam_role.eventbridge_role[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = aws_lambda_function.scheduler.arn
      }
    ]
  })
}

# ========================================
# CloudWatch Log Group
# ========================================

resource "aws_cloudwatch_log_group" "scheduler_logs" {
  name              = "/aws/lambda/nestor-research-scheduler"
  retention_in_days = 7

  tags = {
    Project = "nestor"
    Part    = "4"
    Agent   = "scheduler"
  }
}
