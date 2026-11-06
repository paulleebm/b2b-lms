output "task_definition_arn" {
  value = aws_ecs_task_definition.this.arn
}

output "task_role_arn" {
  value = aws_iam_role.task.arn
}

output "service_name" {
  value = var.run_as_service ? aws_ecs_service.this[0].name : null
}
