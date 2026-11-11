variable "name" {
  type = string
}

variable "alert_email" {
  type = string
}

variable "alb_arn_suffix" {
  type = string
}

variable "dlq_names" {
  type = list(string)
}

variable "monthly_budget_usd" {
  type    = number
  default = 100
}
