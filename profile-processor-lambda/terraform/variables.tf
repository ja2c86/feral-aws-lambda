variable "aws_access_key" {
  description = "AWS Access Key ID"
  default     = "my-terraform-key"
}

variable "aws_secret_access_key" {
  description = "AWS Secret Access Key"
  default     = "my-terraform-secret"
}

variable "aws_region" {
  description = "AWS Region"
  default     = "us-east-1"
}

variable "number_workers" {
  description = "Number of concurrent fibers to process profiles"
  default     = 10
}
