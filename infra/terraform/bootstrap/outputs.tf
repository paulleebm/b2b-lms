output "state_bucket" {
  description = "Put this in envs/demo/backend.hcl"
  value       = aws_s3_bucket.state.bucket
}

output "github_plan_role_arn" {
  description = "Role for pull-request workflows (terraform plan)"
  value       = aws_iam_role.plan.arn
}

output "github_deploy_role_arn" {
  description = "Role for the production deploy workflow (ECR push + ECS rollout)"
  value       = aws_iam_role.deploy.arn
}

output "name_servers" {
  description = "Set these four as the domain's name servers at Gabia"
  value       = aws_route53_zone.this.name_servers
}
