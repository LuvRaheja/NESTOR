variable "aws_region" {
  description = "AWS region for resources"
  type        = string
}

variable "app_runner_url" {
  description = "App Runner service URL for the Researcher agent"
  type        = string
}

variable "scheduler_enabled" {
  description = "Enable automated research scheduler via EventBridge"
  type        = bool
  default     = false
}
