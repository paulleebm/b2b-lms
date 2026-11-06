variable "name" {
  description = "Name prefix for resources"
  type        = string
}

variable "region" {
  description = "AWS region (for the S3 endpoint service name)"
  type        = string
}

variable "cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}
