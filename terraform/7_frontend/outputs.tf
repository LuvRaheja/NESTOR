output "ecr_api_repository_url" {
  description = "ECR repository URL for the API container image"
  value       = aws_ecr_repository.api.repository_url
}

output "api_lambda_function_name" {
  description = "Name of the API Lambda function"
  value       = aws_lambda_function.api.function_name
}

output "api_lambda_arn" {
  description = "ARN of the API Lambda function"
  value       = aws_lambda_function.api.arn
}

output "api_gateway_url" {
  description = "API Gateway endpoint URL"
  value       = aws_apigatewayv2_api.main.api_endpoint
}

output "cloudfront_url" {
  description = "CloudFront distribution URL"
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "s3_bucket_name" {
  description = "Name of the S3 bucket for frontend"
  value       = aws_s3_bucket.frontend.id
}

output "setup_instructions" {
  description = "Instructions for building and deploying"
  value = <<-EOT

    NESTOR infrastructure deployed successfully!

    CloudFront URL: https://${aws_cloudfront_distribution.main.domain_name}
    API Gateway URL: ${aws_apigatewayv2_api.main.api_endpoint}
    S3 Bucket: ${aws_s3_bucket.frontend.id}
    ECR Repository: ${aws_ecr_repository.api.repository_url}
    Lambda Function: ${aws_lambda_function.api.function_name}

    To deploy the full stack, run:
       cd NESTOR/scripts && uv run deploy.py

    Or manually:

    1. Build and push API:
       cd NESTOR
       mvn clean package -pl backend/api -am -DskipTests
       cd backend/api
       docker build --platform linux/amd64 --provenance=false -t nestor-api .
       docker tag nestor-api:latest ${aws_ecr_repository.api.repository_url}:latest
       docker push ${aws_ecr_repository.api.repository_url}:latest
       aws lambda update-function-code --function-name nestor-api --image-uri ${aws_ecr_repository.api.repository_url}:latest

    2. Build and deploy frontend:
       cd NESTOR/frontend
       npm install && npm run build
       aws s3 sync out/ s3://${aws_s3_bucket.frontend.id}/ --delete
       aws cloudfront create-invalidation --distribution-id ${aws_cloudfront_distribution.main.id} --paths "/*"

    3. Test:
       curl ${aws_apigatewayv2_api.main.api_endpoint}/health
       Visit: https://${aws_cloudfront_distribution.main.domain_name}

  EOT
}
