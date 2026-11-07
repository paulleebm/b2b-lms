output "name_servers" {
  description = "Enter these at Gabia (My가비아 → 도메인 관리 → 네임서버 설정), replacing Gabia's own"
  value       = cloudflare_zone.this.name_servers
}

output "app_url" {
  value = "https://${local.app_host}"
}

# Everything the Portainer stack needs except the R2 key pair (created by hand in the dashboard,
# see infra/home/README.md). `terraform output -raw stack_env > ../../../home/stack.env`
output "stack_env" {
  description = "Portainer → Stacks → lms → Environment variables → Advanced mode (or Load variables from .env file)"
  sensitive   = true
  value       = <<-EOT
    IMAGE_PREFIX=${var.image_prefix}
    IMAGE_TAG=latest
    DOMAIN=${var.domain}
    APP_HOST=${local.app_host}
    AUTH_HOST=${local.auth_host}
    POSTGRES_PASSWORD=${random_password.secret["postgres"].result}
    LMS_OWNER_PASSWORD=${random_password.secret["lms-owner"].result}
    LMS_APP_PASSWORD=${random_password.secret["lms-app"].result}
    KEYCLOAK_DB_PASSWORD=${random_password.secret["keycloak-db"].result}
    KEYCLOAK_ADMIN_PASSWORD=${random_password.secret["keycloak-admin"].result}
    LMS_WEB_CLIENT_SECRET=${random_password.secret["web-client"].result}
    LMS_ADMIN_CLIENT_SECRET=${random_password.secret["admin-client"].result}
    LMS_API_CLIENT_SECRET=${random_password.secret["api-client"].result}
    S3_ENDPOINT=${local.r2_endpoint}
    S3_BUCKET=${local.r2_bucket}
    TUNNEL_TOKEN=${data.cloudflare_zero_trust_tunnel_cloudflared_token.home.token}
    # R2 → Manage API tokens → Create (Object Read & Write, bucket ${local.r2_bucket}); paste its two keys:
    S3_ACCESS_KEY_ID=
    S3_SECRET_ACCESS_KEY=
  EOT
}
