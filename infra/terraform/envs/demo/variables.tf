variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "environment" {
  type    = string
  default = "demo"
}

variable "domain" {
  description = "Domain whose Route 53 hosted zone bootstrap/ created, e.g. bymile.site (app./auth./media. are created under it)"
  type        = string
}

variable "image_tag" {
  description = "Image tag to deploy (CI uses the commit SHA; tags are immutable)"
  type        = string
  default     = "bootstrap"
}

variable "alert_email" {
  description = "Receives alarms and budget notifications"
  type        = string
}

variable "monthly_budget_usd" {
  type    = number
  default = 100
}

variable "cloudfront_public_key_pem" {
  description = "Public key for CloudFront signed cookies (generate the pair offline; the private key goes to SSM by hand)"
  type        = string
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "desired_count" {
  description = "Tasks per service. Demo: 1. Production: 2+ spread across AZs, plus autoscaling."
  type        = number
  default     = 1
}
