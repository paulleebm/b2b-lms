variable "name" {
  description = "Service name, e.g. lms-api (also the container and log group name)"
  type        = string
}

variable "cluster_arn" {
  type = string
}

variable "image" {
  description = "Container image with tag"
  type        = string
}

variable "command" {
  description = "Overrides the image command (e.g. the worker or the migration task)"
  type        = list(string)
  default     = null
}

variable "cpu" {
  type = number
}

variable "memory" {
  type = number
}

variable "container_port" {
  type    = number
  default = null
}

variable "extra_ports" {
  description = "Additional container ports (e.g. Keycloak's management port for health checks)"
  type        = list(number)
  default     = []
}

variable "environment" {
  description = "Plain configuration (no secrets)"
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Env var name -> SSM parameter or Secrets Manager ARN (injected at start, never stored in the task definition)"
  type        = map(string)
  default     = {}
}

variable "task_policy_json" {
  description = "IAM policy for the application itself (null = no AWS API access)"
  type        = string
  default     = null
}

variable "run_as_service" {
  description = "false = task definition only (one-off tasks such as migrations)"
  type        = bool
  default     = true
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "subnet_ids" {
  type    = list(string)
  default = []
}

variable "security_group_ids" {
  type    = list(string)
  default = []
}

variable "assign_public_ip" {
  description = "true in the demo (no NAT, see docs/02 D4)"
  type        = bool
  default     = true
}

variable "target_group_arn" {
  type    = string
  default = null
}

variable "health_check_grace_seconds" {
  type    = number
  default = 60
}

variable "service_connect_namespace_arn" {
  type    = string
  default = null
}

variable "service_connect_discovery_name" {
  description = "Name other services use to call this one (null = client only)"
  type        = string
  default     = null
}

variable "log_retention_days" {
  type    = number
  default = 14
}
