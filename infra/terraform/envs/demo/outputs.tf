output "app_url" {
  value = local.app_url
}

output "issuer" {
  value = local.issuer
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

output "ecs_cluster" {
  value = aws_ecs_cluster.this.name
}

output "migrate_task_definition" {
  description = "Run before each API rollout"
  value       = module.migrate.task_definition_arn
}

output "db_master_secret_arn" {
  description = "Only for the one-off role bootstrap (see README)"
  value       = module.data.db_master_secret_arn
}

output "cloudfront_key_pair_id" {
  value = module.storage.cloudfront_key_pair_id
}

output "media_bucket" {
  value = module.storage.bucket_name
}
