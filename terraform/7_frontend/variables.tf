variable "aws_region" {
  description = "AWS region for resources"
  type        = string
}

variable "aurora_cluster_arn" {
  description = "ARN of the Aurora cluster from Part 5"
  type        = string
}

variable "aurora_secret_arn" {
  description = "ARN of the Secrets Manager secret from Part 5"
  type        = string
}

variable "sqs_queue_url" {
  description = "SQS queue URL for analysis jobs (from Part 6)"
  type        = string
}

variable "sqs_queue_arn" {
  description = "SQS queue ARN for analysis jobs (from Part 6)"
  type        = string
}

variable "clerk_jwks_url" {
  description = "Clerk JWKS URL for JWT validation"
  type        = string
}

variable "clerk_issuer" {
  description = "Clerk issuer URL"
  type        = string
  default     = ""
}
