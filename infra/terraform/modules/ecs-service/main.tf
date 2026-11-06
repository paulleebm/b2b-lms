# One Fargate service: task definition, roles, logs, optional ALB target, Service Connect.
# Used for web, api, worker and keycloak so they all get the same defaults: non-root images,
# secrets injected from SSM/Secrets Manager (never in the task definition), circuit-breaker
# rollback, and logs with a retention limit.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_region" "current" {}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
}

data "aws_iam_policy_document" "ecs_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Execution role: what ECS itself needs to start the task (pull image, write logs, read secrets).
resource "aws_iam_role" "execution" {
  name               = "${var.name}-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

locals {
  # "arn:aws:secretsmanager:…:secret:name-AbCd:password::" (a JSON key of a secret) → the secret's ARN.
  secret_resource_arns = distinct([
    for arn in values(var.secrets) : length(regexall(":secret:", arn)) > 0 ? join(":", slice(split(":", arn), 0, 7)) : arn
  ])
}

data "aws_iam_policy_document" "read_secrets" {
  count = length(var.secrets) > 0 ? 1 : 0
  statement {
    sid       = "OnlyThisServicesSecrets"
    actions   = ["ssm:GetParameters", "secretsmanager:GetSecretValue"]
    resources = local.secret_resource_arns
  }
  statement {
    sid       = "DecryptThroughSsmAndSecretsManagerOnly"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values = [
        "ssm.${data.aws_region.current.region}.amazonaws.com",
        "secretsmanager.${data.aws_region.current.region}.amazonaws.com",
      ]
    }
  }
}

resource "aws_iam_role_policy" "read_secrets" {
  count  = length(var.secrets) > 0 ? 1 : 0
  name   = "read-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.read_secrets[0].json
}

# Task role: what the application code may call (S3, SQS, MediaConvert...). Least privilege per service.
resource "aws_iam_role" "task" {
  name               = "${var.name}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy" "task" {
  count  = var.task_policy_json == null ? 0 : 1
  name   = "app"
  role   = aws_iam_role.task.id
  policy = var.task_policy_json
}

locals {
  port_mappings = var.container_port == null ? [] : concat(
    [{ name = var.name, containerPort = var.container_port, protocol = "tcp", appProtocol = "http" }],
    [for p in var.extra_ports : { name = "${var.name}-${p}", containerPort = p, protocol = "tcp" }],
  )
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64" # Graviton: ~20% cheaper per vCPU than x86
  }

  container_definitions = jsonencode([{
    name                   = var.name
    image                  = var.image
    essential              = true
    command                = var.command
    portMappings           = local.port_mappings
    environment            = [for k, v in var.environment : { name = k, value = v }]
    secrets                = [for k, arn in var.secrets : { name = k, valueFrom = arn }]
    readonlyRootFilesystem = false
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.this.name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = var.name
      }
    }
  }])
}

resource "aws_ecs_service" "this" {
  count           = var.run_as_service ? 1 : 0
  name            = var.name
  cluster         = var.cluster_arn
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = var.assign_public_ip
  }

  # A deploy whose tasks keep failing health checks is rolled back automatically.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  dynamic "load_balancer" {
    for_each = var.target_group_arn == null ? [] : [1]
    content {
      target_group_arn = var.target_group_arn
      container_name   = var.name
      container_port   = var.container_port
    }
  }

  health_check_grace_period_seconds = var.target_group_arn == null ? null : var.health_check_grace_seconds

  # Service Connect: services call each other by name (http://api:8081) over the VPC.
  service_connect_configuration {
    enabled   = true
    namespace = var.service_connect_namespace_arn
    dynamic "service" {
      for_each = var.service_connect_discovery_name == null ? [] : [1]
      content {
        port_name      = var.name
        discovery_name = var.service_connect_discovery_name
        client_alias {
          port     = var.container_port
          dns_name = var.service_connect_discovery_name
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [task_definition] # CI registers new revisions; Terraform owns the shape
  }
}
