# PostgreSQL (application DB with RLS + Keycloak DB) and Valkey (sessions, caches) in private
# subnets. Both reachable only from the security groups passed in.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

# --- PostgreSQL -----------------------------------------------------------------------------------

resource "aws_security_group" "db" {
  name        = "${var.name}-db"
  description = "PostgreSQL: only from app services"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "db" {
  for_each                     = toset(var.db_client_security_group_ids)
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = each.value # security groups, not IPs: survives task restarts
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-db"
  subnet_ids = var.private_subnet_ids
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name}-pg17"
  family = "postgres17"
  parameter {
    name  = "rds.force_ssl" # no plaintext connections, even inside the VPC
    value = "1"
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name}-db"
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true

  username                    = "postgres"
  manage_master_user_password = true # master password lives in Secrets Manager, rotated by RDS

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = var.multi_az # demo: single AZ; production: true

  backup_retention_period    = var.backup_retention_days
  auto_minor_version_upgrade = true
  deletion_protection        = var.deletion_protection
  skip_final_snapshot        = !var.deletion_protection
  final_snapshot_identifier  = var.deletion_protection ? "${var.name}-db-final" : null
}

# --- Valkey (Redis-compatible) --------------------------------------------------------------------

resource "aws_security_group" "cache" {
  name        = "${var.name}-cache"
  description = "Valkey: only from app services"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "cache" {
  for_each                     = toset(var.cache_client_security_group_ids)
  security_group_id            = aws_security_group.cache.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name}-cache"
  subnet_ids = var.private_subnet_ids
}

resource "random_password" "cache_auth" {
  length  = 40
  special = false # ElastiCache auth tokens allow only a few special characters; keep it simple
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${var.name}-cache"
  description          = "BFF sessions, refresh locks, caches"
  engine               = "valkey"
  engine_version       = "8.0"
  node_type            = var.cache_node_type
  num_cache_clusters   = 1 # demo: one node; production: 2+ with automatic failover
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.cache.id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.cache_auth.result
}
