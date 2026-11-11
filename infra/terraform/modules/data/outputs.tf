output "db_address" {
  value = aws_db_instance.this.address
}

output "db_master_secret_arn" {
  description = "Secrets Manager secret with the master credentials (used only by the one-off role bootstrap)"
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "cache_url" {
  description = "rediss:// URL with the auth token (TLS)"
  value       = "rediss://:${random_password.cache_auth.result}@${aws_elasticache_replication_group.this.primary_endpoint_address}:6379"
  sensitive   = true
}
