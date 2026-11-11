variable "region" {
  description = "AWS region for the state bucket and the roles"
  type        = string
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  description = "Globally unique S3 bucket name for Terraform state, e.g. lms-tfstate-<account-id>"
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the CI roles (owner/name)"
  type        = string
  default     = "paulleebm/b2b-lms"
}

variable "domain" {
  description = "Registered domain (bought at Gabia). Its hosted zone lives here, not in envs/, so tearing down the demo never changes the name servers set at the registrar"
  type        = string
  default     = "bymile.site"
}
