output "ecr_repository_url" {
  description = "ECR repository URL for the scheduler container image"
  value       = aws_ecr_repository.scheduler.repository_url
}

output "lambda_function_name" {
  description = "Name of the scheduler Lambda function"
  value       = aws_lambda_function.scheduler.function_name
}

output "setup_instructions" {
  description = "Instructions for building and deploying the scheduler"
  value = <<-EOT

    NESTOR Scheduler infrastructure deployed successfully!

    ECR Repository: ${aws_ecr_repository.scheduler.repository_url}

    Lambda Function:
    - Scheduler: ${aws_lambda_function.scheduler.function_name}

    To build and push the scheduler container image:

    1. Build the JAR:
       cd NESTOR
       mvn clean package -pl backend/scheduler -am -DskipTests

    2. Authenticate Docker with ECR:
       aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com

    3. Build and push the Docker image:
       cd NESTOR/backend/scheduler
       docker build --platform linux/amd64 --provenance=false -t nestor-scheduler .
       docker tag nestor-scheduler:latest ${aws_ecr_repository.scheduler.repository_url}:latest
       docker push ${aws_ecr_repository.scheduler.repository_url}:latest

    4. Update the Lambda to use the new image:
       aws lambda update-function-code --function-name nestor-research-scheduler --image-uri ${aws_ecr_repository.scheduler.repository_url}:latest

    5. Monitor in CloudWatch Logs:
       - /aws/lambda/nestor-research-scheduler
  EOT
}
