output "vector_bucket_name" {
  description = "Name of the S3 Vectors bucket"
  value       = aws_s3_bucket.vectors.id
}

output "ecr_repository_url" {
  description = "ECR repository URL for the ingest container image"
  value       = aws_ecr_repository.ingest.repository_url
}

output "api_endpoint" {
  description = "API Gateway endpoint URL"
  value       = "${aws_api_gateway_stage.api.invoke_url}/ingest"
}

output "api_key_id" {
  description = "API Key ID"
  value       = aws_api_gateway_api_key.api_key.id
}

output "api_key_value" {
  description = "API Key value (sensitive)"
  value       = aws_api_gateway_api_key.api_key.value
  sensitive   = true
}

output "setup_instructions" {
  description = "Instructions for building and deploying the ingest container"
  value       = <<-EOT

    ✅ Ingestion pipeline deployed successfully!

    Add the following to your .env file:
    VECTOR_BUCKET=${aws_s3_bucket.vectors.id}
    NESTOR_API_ENDPOINT=${aws_api_gateway_stage.api.invoke_url}/ingest

    To get your API key value:
    aws apigateway get-api-key --api-key ${aws_api_gateway_api_key.api_key.id} --include-value --query 'value' --output text

    Build and push the container image:

    # 1. Build the fat JAR (from NESTOR/backend/)
    cd backend && mvn clean package -pl ingest -am -DskipTests

    # 2. Authenticate Docker with ECR
    aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${aws_ecr_repository.ingest.repository_url}

    # 3. Build the Docker image
    cd backend/ingest && docker build --platform linux/amd64 -t nestor-ingest .

    # 4. Tag and push
    docker tag nestor-ingest:latest ${aws_ecr_repository.ingest.repository_url}:latest
    docker push ${aws_ecr_repository.ingest.repository_url}:latest

    # 5. Update Lambda to use new image
    aws lambda update-function-code --function-name nestor-ingest --image-uri ${aws_ecr_repository.ingest.repository_url}:latest --region ${var.aws_region}

    Test the API:
    curl -X POST ${aws_api_gateway_stage.api.invoke_url}/ingest \
      -H "x-api-key: <your-api-key>" \
      -H "Content-Type: application/json" \
      -d '{"text": "Test document about financial markets", "metadata": {"source": "test"}}'
  EOT
}
