###############################################################################
# PixelMentor – Input Variables
###############################################################################

variable "environment" {
  description = "Deployment environment (dev | staging | prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod"
  }
}

variable "location" {
  description = "Azure region for all resources"
  type        = string
  default     = "australiaeast"   # Change to your preferred region
}

variable "openai_sku" {
  description = "Azure OpenAI SKU"
  type        = string
  default     = "S0"
}

variable "gpt4o_capacity" {
  description = "GPT-4o deployment capacity (thousands of tokens per minute)"
  type        = number
  default     = 40
}

variable "cosmos_throughput" {
  description = "Cosmos DB serverless — no manual throughput needed"
  type        = string
  default     = "serverless"
}

variable "container_image_tag" {
  description = "Docker image tag to deploy"
  type        = string
  default     = "latest"
}

variable "alert_email" {
  description = "Email address for monitoring alerts"
  type        = string
}

variable "suffix_override" {
  description = "Override the random suffix to force new resource names (use when old names are stuck in soft-delete)"
  type        = string
  default     = ""
}
