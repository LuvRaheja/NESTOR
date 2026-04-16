output "ecr_repository_url" {
  description = "ECR repository URL for the tagger container image"
  value       = aws_ecr_repository.tagger.repository_url
}

output "ecr_charter_repository_url" {
  description = "ECR repository URL for the charter container image"
  value       = aws_ecr_repository.charter.repository_url
}

output "ecr_retirement_repository_url" {
  description = "ECR repository URL for the retirement container image"
  value       = aws_ecr_repository.retirement.repository_url
}

output "ecr_reporter_repository_url" {
  description = "ECR repository URL for the reporter container image"
  value       = aws_ecr_repository.reporter.repository_url
}

output "ecr_planner_repository_url" {
  description = "ECR repository URL for the planner container image"
  value       = aws_ecr_repository.planner.repository_url
}

output "sqs_queue_url" {
  description = "SQS queue URL for analysis jobs"
  value       = aws_sqs_queue.analysis_jobs.url
}

output "sqs_queue_arn" {
  description = "SQS queue ARN for analysis jobs"
  value       = aws_sqs_queue.analysis_jobs.arn
}

output "lambda_functions" {
  description = "Names of deployed Lambda functions"
  value = {
    tagger     = aws_lambda_function.tagger.function_name
    charter    = aws_lambda_function.charter.function_name
    retirement = aws_lambda_function.retirement.function_name
    reporter   = aws_lambda_function.reporter.function_name
    planner    = aws_lambda_function.planner.function_name
  }
}

output "setup_instructions" {
  description = "Instructions for building and deploying the agents"
  value = <<-EOT

    NESTOR Agent infrastructure deployed successfully!

    ECR Repositories:
    - Tagger:     ${aws_ecr_repository.tagger.repository_url}
    - Charter:    ${aws_ecr_repository.charter.repository_url}
    - Retirement: ${aws_ecr_repository.retirement.repository_url}
    - Reporter:   ${aws_ecr_repository.reporter.repository_url}

    Lambda Functions:
    - Tagger:     ${aws_lambda_function.tagger.function_name}
    - Charter:    ${aws_lambda_function.charter.function_name}
    - Retirement: ${aws_lambda_function.retirement.function_name}
    - Reporter:   ${aws_lambda_function.reporter.function_name}

    To build and push a container image (example: reporter):

    1. Build the JAR:
       cd NESTOR
       mvn clean package -pl backend/reporter -am -DskipTests

    2. Authenticate Docker with ECR:
       aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com

    3. Build and push the Docker image:
       cd NESTOR/backend/reporter
       docker build --platform linux/amd64 --provenance=false -t nestor-reporter .
       docker tag nestor-reporter:latest ${aws_ecr_repository.reporter.repository_url}:latest
       docker push ${aws_ecr_repository.reporter.repository_url}:latest

    4. Update the Lambda to use the new image:
       aws lambda update-function-code --function-name nestor-reporter --image-uri ${aws_ecr_repository.reporter.repository_url}:latest

    5. Monitor in CloudWatch Logs:
       - /aws/lambda/nestor-tagger
       - /aws/lambda/nestor-charter
       - /aws/lambda/nestor-retirement
       - /aws/lambda/nestor-reporter

    Bedrock Model: ${var.bedrock_model_id}
    Region: ${var.bedrock_region}
  EOT
}
