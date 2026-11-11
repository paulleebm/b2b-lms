# The demo environment: everything in docs/03 §2 wired together.
#
#   Route 53 ─ app.<domain>  ─┐
#             auth.<domain> ─┤ ALB (TLS) ─ web (Next.js BFF) ─ Service Connect ─ api (Spring)
#                            └───────────── keycloak
#             media.<domain> ─ CloudFront (signed cookies) ─ S3 (HLS renditions)
#   private subnets: RDS PostgreSQL (lms + keycloak DBs), ElastiCache Valkey
#   async: SQS (+DLQ), MediaConvert queue, EventBridge rule

locals {
  name       = "lms-${var.environment}"
  app_host   = "app.${var.domain}"
  auth_host  = "auth.${var.domain}"
  media_host = "media.${var.domain}"
  app_url    = "https://${local.app_host}"
  issuer     = "https://${local.auth_host}/realms/lms"
  registry   = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  ssm_prefix = "/lms/${var.environment}"
}

data "aws_caller_identity" "current" {}

data "aws_route53_zone" "this" {
  name = var.domain
}

module "network" {
  source = "../../modules/network"
  name   = local.name
  region = var.region
}

module "edge" {
  source            = "../../modules/edge"
  name              = local.name
  vpc_id            = module.network.vpc_id
  vpc_cidr          = "10.0.0.0/16"
  public_subnet_ids = module.network.public_subnet_ids
  zone_id           = data.aws_route53_zone.this.zone_id
  app_host          = local.app_host
  auth_host         = local.auth_host
}

# --- Security groups: each service accepts only what it needs, by group reference -------------

resource "aws_security_group" "web" {
  name        = "${local.name}-web"
  description = "Next.js BFF: from the ALB only"
  vpc_id      = module.network.vpc_id
}

resource "aws_security_group" "api" {
  name        = "${local.name}-api"
  description = "Spring API: from the BFF only (no public route)"
  vpc_id      = module.network.vpc_id
}

resource "aws_security_group" "keycloak" {
  name        = "${local.name}-keycloak"
  description = "Keycloak: from the ALB only"
  vpc_id      = module.network.vpc_id
}

resource "aws_security_group" "migrate" {
  name        = "${local.name}-migrate"
  description = "One-off migration task: no inbound"
  vpc_id      = module.network.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "web_from_alb" {
  security_group_id            = aws_security_group.web.id
  referenced_security_group_id = module.edge.alb_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 3000
  to_port                      = 3000
}

resource "aws_vpc_security_group_ingress_rule" "api_from_web" {
  security_group_id            = aws_security_group.api.id
  referenced_security_group_id = aws_security_group.web.id
  ip_protocol                  = "tcp"
  from_port                    = 8081
  to_port                      = 8081
}

resource "aws_vpc_security_group_ingress_rule" "keycloak_from_alb" {
  for_each                     = toset(["8080", "9000"]) # traffic + management (health) port
  security_group_id            = aws_security_group.keycloak.id
  referenced_security_group_id = module.edge.alb_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
}

# Outbound: AWS APIs, Keycloak's public issuer URL (JWKS), the database and cache.
resource "aws_vpc_security_group_egress_rule" "all" {
  for_each = {
    web      = aws_security_group.web.id
    api      = aws_security_group.api.id
    keycloak = aws_security_group.keycloak.id
    migrate  = aws_security_group.migrate.id
  }
  security_group_id = each.value
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

module "data" {
  source             = "../../modules/data"
  name               = local.name
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  db_instance_class  = var.db_instance_class
  db_client_security_group_ids = [
    aws_security_group.api.id, aws_security_group.keycloak.id, aws_security_group.migrate.id,
  ]
  cache_client_security_group_ids = [aws_security_group.web.id, aws_security_group.api.id]
}

module "storage" {
  source = "../../modules/storage"
  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }
  name                      = local.name
  bucket_name               = "${local.name}-media-${data.aws_caller_identity.current.account_id}"
  app_origin                = local.app_url
  media_host                = local.media_host
  zone_id                   = data.aws_route53_zone.this.zone_id
  cloudfront_public_key_pem = var.cloudfront_public_key_pem
}

module "async" {
  source           = "../../modules/async"
  name             = local.name
  media_bucket_arn = module.storage.bucket_arn
}

# --- Secrets: generated here, stored as SSM SecureString, injected into tasks at start ----------
# They also end up in the Terraform state, which is why the state bucket is KMS-encrypted and
# access to it is limited to the plan role and administrators.

resource "random_password" "secret" {
  for_each = toset(["db-app", "db-owner", "db-keycloak", "kc-web-client", "kc-admin-client", "kc-bootstrap-admin"])
  length   = 32
  special  = false
}

resource "aws_ssm_parameter" "secret" {
  for_each = random_password.secret
  name     = "${local.ssm_prefix}/${each.key}"
  type     = "SecureString"
  value    = each.value.result
}

resource "aws_ssm_parameter" "redis_url" {
  name  = "${local.ssm_prefix}/redis-url"
  type  = "SecureString"
  value = module.data.cache_url
}

# Set by hand after apply (aws ssm put-parameter --overwrite): the private key never touches Terraform.
resource "aws_ssm_parameter" "cloudfront_private_key" {
  name  = "${local.ssm_prefix}/cloudfront-private-key"
  type  = "SecureString"
  value = "set-me-out-of-band"
  lifecycle {
    ignore_changes = [value]
  }
}

# --- Images -----------------------------------------------------------------------------------

resource "aws_ecr_repository" "this" {
  for_each             = toset(["web", "api", "keycloak"])
  name                 = "lms/${each.key}"
  image_tag_mutability = "IMMUTABLE" # a tag always means the same image (rollbacks are exact)
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 20 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 20 }
      action       = { type = "expire" }
    }]
  })
}

# --- ECS --------------------------------------------------------------------------------------

resource "aws_service_discovery_http_namespace" "this" {
  name = local.name
}

resource "aws_ecs_cluster" "this" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  service_connect_defaults {
    namespace = aws_service_discovery_http_namespace.this.arn
  }
}

locals {
  db_url = "jdbc:postgresql://${module.data.db_address}:5432"

  api_environment = {
    DB_URL                   = "${local.db_url}/lms?sslmode=require"
    DB_APP_USER              = "lms_app"
    FLYWAY_ENABLED           = "false" # migrations run as a separate one-off task (below)
    KEYCLOAK_ISSUER          = local.issuer
    KEYCLOAK_BASE_URL        = "https://${local.auth_host}"
    KEYCLOAK_ADMIN_CLIENT_ID = "lms-admin"
    KEYCLOAK_WEB_CLIENT_ID   = "lms-web"
    APP_URL                  = local.app_url
    S3_BUCKET                = module.storage.bucket_name
    S3_ENDPOINT              = "" # empty = real S3 with the task role
    AWS_REGION               = var.region
    VIDEO_TRANSCODER         = "mediaconvert"
    # Spring reads these via relaxed binding (dots/dashes → underscores, upper case).
    LMS_MEDIA_MEDIACONVERT_ROLE_ARN  = module.async.mediaconvert_role_arn
    LMS_MEDIA_MEDIACONVERT_QUEUE_ARN = module.async.mediaconvert_queue_arn
    LMS_MEDIA_CDN_BASE_URL           = module.storage.media_base_url
    LMS_MEDIA_CDN_KEY_PAIR_ID        = module.storage.cloudfront_key_pair_id
    LMS_MEDIA_CDN_COOKIE_DOMAIN      = ".${var.domain}" # app. sets it, media. receives it
    SQS_ENDPOINT                     = ""               # real SQS with the task role
    SQS_EVENTS_QUEUE                 = module.async.events_queue_name
    SQS_TRANSCODE_QUEUE              = module.async.transcode_queue_name
    WORKER_ENABLED                   = "false" # the worker service below consumes the queues
    CACHE_TYPE                       = "redis"
    # One JSON object per line (Elastic Common Schema) with the MDC trace and tenant IDs as
    # fields, so CloudWatch Logs Insights can filter "tenantId = …" without regex parsing.
    LOGGING_STRUCTURED_FORMAT_CONSOLE = "ecs"
    MAIL_HOST                         = "email-smtp.${var.region}.amazonaws.com" # SES SMTP interface
    MAIL_PORT                         = "587"
    MAIL_STARTTLS                     = "true"
    MAIL_FROM                         = "LMS <no-reply@${var.domain}>"
    SWAGGER_UI_ENABLED                = "false"
    JAVA_TOOL_OPTIONS                 = "-XX:MaxRAMPercentage=75"
  }
  api_secrets = {
    DB_APP_PASSWORD               = aws_ssm_parameter.secret["db-app"].arn
    KEYCLOAK_ADMIN_CLIENT_SECRET  = aws_ssm_parameter.secret["kc-admin-client"].arn
    LMS_MEDIA_CDN_PRIVATE_KEY_PEM = aws_ssm_parameter.cloudfront_private_key.arn
    MAIL_USERNAME                 = aws_ssm_parameter.ses_smtp["username"].arn
    MAIL_PASSWORD                 = aws_ssm_parameter.ses_smtp["password"].arn
    REDIS_URL                     = aws_ssm_parameter.redis_url.arn
  }
}

data "aws_iam_policy_document" "api_task" {
  statement {
    sid = "MediaObjects"
    actions = [
      "s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:AbortMultipartUpload",
      "s3:ListMultipartUploadParts",
    ]
    resources = ["${module.storage.bucket_arn}/tenants/*"]
  }
  statement {
    sid       = "PublishDomainEvents"
    actions   = ["sqs:SendMessage", "sqs:GetQueueUrl"]
    resources = [module.async.events_queue_arn]
  }
  statement {
    sid       = "StartTranscodes"
    actions   = ["mediaconvert:CreateJob", "mediaconvert:GetJob"]
    resources = ["*"]
  }
  statement {
    sid       = "HandMediaConvertItsRole"
    actions   = ["iam:PassRole"]
    resources = [module.async.mediaconvert_role_arn]
  }
}

# The worker: the same image with the queue consumers on (WORKER_ENABLED=true) and no inbound port.
data "aws_iam_policy_document" "worker_task" {
  source_policy_documents = [data.aws_iam_policy_document.api_task.json]
  statement {
    sid       = "ConsumeQueues"
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueUrl", "sqs:ChangeMessageVisibility"]
    resources = [module.async.events_queue_arn, module.async.transcode_queue_arn]
  }
}

module "worker" {
  source                        = "../../modules/ecs-service"
  name                          = "${local.name}-worker"
  cluster_arn                   = aws_ecs_cluster.this.arn
  image                         = "${local.registry}/lms/api:${var.image_tag}"
  command                       = ["--server.port=8081"]
  cpu                           = 256
  memory                        = 1024
  desired_count                 = 1
  subnet_ids                    = module.network.public_subnet_ids
  security_group_ids            = [aws_security_group.migrate.id] # no inbound, outbound only
  service_connect_namespace_arn = aws_service_discovery_http_namespace.this.arn
  environment                   = merge(local.api_environment, { WORKER_ENABLED = "true" })
  secrets                       = local.api_secrets
  task_policy_json              = data.aws_iam_policy_document.worker_task.json
}

# SES SMTP credentials are created in the SES console (or an IAM user with ses:SendRawEmail) and
# put here by hand: Terraform never sees them.
resource "aws_ssm_parameter" "ses_smtp" {
  for_each = toset(["username", "password"])
  name     = "${local.ssm_prefix}/ses-smtp-${each.key}"
  type     = "SecureString"
  value    = "set-me-out-of-band"
  lifecycle {
    ignore_changes = [value]
  }
}

module "api" {
  source                         = "../../modules/ecs-service"
  name                           = "${local.name}-api"
  cluster_arn                    = aws_ecs_cluster.this.arn
  image                          = "${local.registry}/lms/api:${var.image_tag}"
  cpu                            = 512
  memory                         = 1024
  container_port                 = 8081
  desired_count                  = var.desired_count
  subnet_ids                     = module.network.public_subnet_ids
  security_group_ids             = [aws_security_group.api.id]
  service_connect_namespace_arn  = aws_service_discovery_http_namespace.this.arn
  service_connect_discovery_name = "api" # the BFF calls http://api:8081
  environment                    = local.api_environment
  secrets                        = local.api_secrets
  task_policy_json               = data.aws_iam_policy_document.api_task.json
}

# Flyway as its own task, with the owner account the API never gets. CI runs it before rolling
# out a new API version: `aws ecs run-task --task-definition lms-demo-migrate ...`.
module "migrate" {
  source         = "../../modules/ecs-service"
  name           = "${local.name}-migrate"
  cluster_arn    = aws_ecs_cluster.this.arn
  image          = "${local.registry}/lms/api:${var.image_tag}"
  command        = ["--lms.migrate-only=true", "--server.port=0"] # random port: it exits right after migrating
  cpu            = 256
  memory         = 512
  run_as_service = false
  environment = merge(local.api_environment, {
    FLYWAY_ENABLED = "true"
    DB_OWNER_USER  = "lms_owner"
  })
  secrets = merge(local.api_secrets, {
    DB_OWNER_PASSWORD = aws_ssm_parameter.secret["db-owner"].arn
  })
}

module "web" {
  source                        = "../../modules/ecs-service"
  name                          = "${local.name}-web"
  cluster_arn                   = aws_ecs_cluster.this.arn
  image                         = "${local.registry}/lms/web:${var.image_tag}"
  cpu                           = 256
  memory                        = 512
  container_port                = 3000
  desired_count                 = var.desired_count
  subnet_ids                    = module.network.public_subnet_ids
  security_group_ids            = [aws_security_group.web.id]
  target_group_arn              = module.edge.web_target_group_arn
  service_connect_namespace_arn = aws_service_discovery_http_namespace.this.arn # client of "api"
  environment = {
    APP_URL            = local.app_url
    KEYCLOAK_ISSUER    = local.issuer
    KEYCLOAK_CLIENT_ID = "lms-web"
    API_URL            = "http://api:8081"
  }
  secrets = {
    KEYCLOAK_CLIENT_SECRET = aws_ssm_parameter.secret["kc-web-client"].arn
    REDIS_URL              = aws_ssm_parameter.redis_url.arn
  }
}

module "keycloak" {
  source                        = "../../modules/ecs-service"
  name                          = "${local.name}-keycloak"
  cluster_arn                   = aws_ecs_cluster.this.arn
  image                         = "${local.registry}/lms/keycloak:${var.image_tag}"
  command                       = ["start", "--optimized"]
  cpu                           = 512
  memory                        = 1024
  container_port                = 8080
  extra_ports                   = [9000]
  desired_count                 = 1
  subnet_ids                    = module.network.public_subnet_ids
  security_group_ids            = [aws_security_group.keycloak.id]
  target_group_arn              = module.edge.keycloak_target_group_arn
  health_check_grace_seconds    = 180 # first start creates its schema
  service_connect_namespace_arn = aws_service_discovery_http_namespace.this.arn
  environment = {
    KC_DB                       = "postgres"
    KC_DB_URL                   = "${local.db_url}/keycloak?sslmode=require"
    KC_DB_USERNAME              = "keycloak"
    KC_HOSTNAME                 = "https://${local.auth_host}"
    KC_PROXY_HEADERS            = "xforwarded" # TLS ends at the ALB
    KC_HTTP_ENABLED             = "true"
    KC_BOOTSTRAP_ADMIN_USERNAME = "admin"
  }
  secrets = {
    KC_DB_PASSWORD              = aws_ssm_parameter.secret["db-keycloak"].arn
    KC_BOOTSTRAP_ADMIN_PASSWORD = aws_ssm_parameter.secret["kc-bootstrap-admin"].arn
  }
}

module "observability" {
  source             = "../../modules/observability"
  name               = local.name
  alert_email        = var.alert_email
  alb_arn_suffix     = module.edge.alb_arn_suffix
  dlq_names          = module.async.dlq_names
  monthly_budget_usd = var.monthly_budget_usd
}
