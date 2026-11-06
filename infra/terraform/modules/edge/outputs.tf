output "alb_security_group_id" {
  value = aws_security_group.alb.id
}

output "alb_arn_suffix" {
  value = aws_lb.this.arn_suffix
}

output "web_target_group_arn" {
  value = aws_lb_target_group.web.arn
}

output "keycloak_target_group_arn" {
  value = aws_lb_target_group.keycloak.arn
}
