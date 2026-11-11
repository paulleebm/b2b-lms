variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "zone_id" {
  description = "Route 53 hosted zone of the domain"
  type        = string
}

variable "app_host" {
  description = "e.g. app.example.com"
  type        = string
}

variable "auth_host" {
  description = "e.g. auth.example.com"
  type        = string
}
