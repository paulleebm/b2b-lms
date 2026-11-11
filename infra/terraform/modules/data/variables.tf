variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "db_client_security_group_ids" {
  description = "Security groups allowed to connect to PostgreSQL (api, worker, keycloak, migrations)"
  type        = list(string)
}

variable "cache_client_security_group_ids" {
  description = "Security groups allowed to connect to Valkey (web, api, worker)"
  type        = list(string)
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "multi_az" {
  type    = bool
  default = false
}

variable "backup_retention_days" {
  type    = number
  default = 1
}

variable "deletion_protection" {
  description = "Demo: false so `terraform destroy` works after the interview. Production: true."
  type        = bool
  default     = false
}

variable "cache_node_type" {
  type    = string
  default = "cache.t4g.micro"
}
