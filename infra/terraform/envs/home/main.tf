# Home deployment: frontend on Vercel, backend on a mini PC (Portainer stack in infra/home),
# Cloudflare in front of the mini PC. See infra/home/README.md for the order of steps and why.
#
#   Browser ─▶ app.<domain>  (Vercel, Seoul functions) ──▶ api.<domain> + service token ─┐
#           ─▶ auth.<domain> (Keycloak)                                                  ├▶ Cloudflare ─▶ tunnel ─▶ mini PC
#           ─▶ <account>.r2.cloudflarestorage.com (presigned uploads/downloads)          │
#   Admin   ─▶ auth.<domain>/admin, mail.<domain> (Access: one-time code to admin_email) ┘
#
# Credentials come from the environment, never from files:
#   CLOUDFLARE_API_TOKEN, VERCEL_API_TOKEN, TF_VAR_upstash_api_key
# State is local (terraform.tfstate, gitignored) and DOES hold secrets: the generated passwords,
# the tunnel token and the service token. Keep it on this machine; see README for why.

provider "cloudflare" {}
provider "vercel" {}
provider "upstash" {
  email   = var.upstash_email
  api_key = var.upstash_api_key
}

locals {
  app_host  = "app.${var.domain}"
  auth_host = "auth.${var.domain}"
  api_host  = "api.${var.domain}"
  mail_host = "mail.${var.domain}"

  # Published through the tunnel → container on the stack's Docker network
  tunnel_routes = {
    (local.auth_host) = "http://keycloak:8080"
    (local.api_host)  = "http://api:8081"
    (local.mail_host) = "http://mailpit:8025"
  }

  r2_endpoint = "https://${var.cloudflare_account_id}.r2.cloudflarestorage.com"
  r2_bucket   = "lms-media"
}

# ── Secrets shared by the stack and Vercel ─────────────────────────────────────────────────────
# Letters and digits only: they go into env files, JDBC/Redis URLs and the realm JSON unescaped.
resource "random_password" "secret" {
  for_each = toset([
    "postgres", "lms-owner", "lms-app", "keycloak-db", "keycloak-admin",
    "web-client", "admin-client", "api-client",
  ])
  length  = 40
  special = false
}

# ── DNS: Cloudflare answers for the domain (Gabia stays the registrar) ─────────────────────────
resource "cloudflare_zone" "this" {
  account = { id = var.cloudflare_account_id }
  name    = var.domain

  # Re-creating the zone changes its nameservers, and the domain is dead until Gabia is updated.
  lifecycle {
    prevent_destroy = true
  }
}

# ── Tunnel: cloudflared on the mini PC dials out; no inbound port, home IP never published ────
resource "random_bytes" "tunnel_secret" {
  length = 32
}

resource "cloudflare_zero_trust_tunnel_cloudflared" "home" {
  account_id    = var.cloudflare_account_id
  name          = "lms-home"
  config_src    = "cloudflare" # routes below are pushed to cloudflared; the container holds only a token
  tunnel_secret = random_bytes.tunnel_secret.base64
}

data "cloudflare_zero_trust_tunnel_cloudflared_token" "home" {
  account_id = var.cloudflare_account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.home.id
}

resource "cloudflare_zero_trust_tunnel_cloudflared_config" "home" {
  account_id = var.cloudflare_account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.home.id

  config = {
    ingress = concat(
      [for host, service in local.tunnel_routes : { hostname = host, service = service }],
      [{ service = "http_status:404" }], # anything else that reaches the tunnel
    )
  }
}

resource "cloudflare_dns_record" "tunnel" {
  for_each = local.tunnel_routes

  zone_id = cloudflare_zone.this.id
  name    = each.key
  type    = "CNAME"
  content = "${cloudflare_zero_trust_tunnel_cloudflared.home.id}.cfargotunnel.com"
  proxied = true
  ttl     = 1 # automatic (required for proxied records)
}

# Vercel serves app.<domain>. DNS only (grey cloud): Vercel terminates TLS and issues the
# certificate itself, and proxying through Cloudflare would stack two CDNs.
resource "cloudflare_dns_record" "app" {
  zone_id = cloudflare_zone.this.id
  name    = local.app_host
  type    = "CNAME"
  content = "cname.vercel-dns.com"
  proxied = false
  ttl     = 1
}

# ── Access: who may reach what, decided at Cloudflare's edge before the tunnel ─────────────────
# The API has a public name only because Vercel is outside the home network. Access admits
# requests that carry this service token (sent by the BFF) and nothing else, which keeps the
# AWS design's "the API has no public entrance" property.
resource "cloudflare_zero_trust_access_service_token" "bff" {
  account_id = var.cloudflare_account_id
  name       = "lms-vercel-bff"
  duration   = "8760h" # one year; rotate by replacing the resource (Vercel env follows)
}

resource "cloudflare_zero_trust_access_policy" "bff" {
  account_id = var.cloudflare_account_id
  name       = "LMS BFF service token"
  decision   = "non_identity"
  include = [{
    service_token = { token_id = cloudflare_zero_trust_access_service_token.bff.id }
  }]
}

resource "cloudflare_zero_trust_access_application" "api" {
  account_id       = var.cloudflare_account_id
  name             = "LMS API"
  type             = "self_hosted"
  destinations     = [{ type = "public", uri = local.api_host }]
  session_duration = "24h"
  policies         = [{ id = cloudflare_zero_trust_access_policy.bff.id, precedence = 1 }]
}

resource "cloudflare_zero_trust_access_policy" "owner" {
  account_id       = var.cloudflare_account_id
  name             = "LMS owner"
  decision         = "allow"
  include          = [{ email = { email = var.admin_email } }]
  session_duration = "12h"
}

# The Keycloak admin console (the login pages under /realms stay public) and the demo inbox.
# The ALB in the AWS design blocks /admin outright; here the owner can still get in.
resource "cloudflare_zero_trust_access_application" "admin" {
  account_id = var.cloudflare_account_id
  name       = "LMS admin consoles"
  type       = "self_hosted"
  destinations = [
    { type = "public", uri = "${local.auth_host}/admin" },
    { type = "public", uri = local.mail_host },
  ]
  session_duration = "12h"
  policies         = [{ id = cloudflare_zero_trust_access_policy.owner.id, precedence = 1 }]
}

# ── Object storage: R2 (S3 API). Uploads and downloads go browser ↔ R2 with presigned URLs ─────
# Why not on the mini PC: an S3 server open to the internet would have to verify signatures
# (S3Mock does not) and stay patched (MinIO's community edition is discontinued), and video
# bytes would go through a home uplink. R2 has no egress fees, like CloudFront in front of S3.
resource "cloudflare_r2_bucket" "media" {
  account_id = var.cloudflare_account_id
  name       = local.r2_bucket
  location   = "apac"
}

resource "cloudflare_r2_bucket_cors" "media" {
  account_id  = var.cloudflare_account_id
  bucket_name = cloudflare_r2_bucket.media.name
  rules = [{
    allowed = {
      origins = ["https://${local.app_host}"]
      methods = ["GET", "PUT", "HEAD"]
      headers = ["content-type", "content-md5"]
    }
    expose_headers  = ["ETag"] # multipart upload: the browser reads each part's ETag
    max_age_seconds = 3600
  }]
}

# ── Session store for the BFF: Upstash Redis (TLS), since Vercel functions keep no state ───────
resource "upstash_redis_database" "sessions" {
  database_name  = "lms-sessions"
  region         = "global"
  primary_region = var.upstash_region
  tls            = true
  eviction       = false # sessions must not be evicted to make room; they expire by TTL
}

locals {
  redis_url = "rediss://default:${upstash_redis_database.sessions.password}@${upstash_redis_database.sessions.endpoint}:${upstash_redis_database.sessions.port}"
}

# ── Vercel: the Next.js BFF ────────────────────────────────────────────────────────────────────
resource "vercel_project" "web" {
  name           = "lms-web"
  framework      = "nextjs"
  root_directory = "frontend/apps/web" # pnpm workspace root (frontend/) is found from the lockfile
  node_version   = "22.x"

  git_repository = {
    type              = "github"
    repo              = var.github_repo
    production_branch = var.production_branch
  }

  # Functions next to the API and Keycloak (Seoul). Fluid compute reuses instances, so the
  # OIDC discovery and Redis connection cached on globalThis survive between requests.
  resource_config = {
    fluid                    = true
    function_default_regions = ["icn1"]
  }

  # Preview deployments are off: each preview gets its own *.vercel.app URL, and allowing those
  # as OIDC redirect URIs would mean a wildcard redirect, the classic way to leak auth codes.
  preview_deployments_disabled = true
}

resource "vercel_project_environment_variables" "web" {
  project_id = vercel_project.web.id
  variables = [
    for key, value in {
      APP_URL                 = "https://${local.app_host}"
      KEYCLOAK_ISSUER         = "https://${local.auth_host}/realms/lms" # also read at build time (CSP)
      KEYCLOAK_CLIENT_ID      = "lms-web"
      KEYCLOAK_CLIENT_SECRET  = random_password.secret["web-client"].result
      REDIS_URL               = local.redis_url
      API_URL                 = "https://${local.api_host}"
      CF_ACCESS_CLIENT_ID     = cloudflare_zero_trust_access_service_token.bff.client_id
      CF_ACCESS_CLIENT_SECRET = cloudflare_zero_trust_access_service_token.bff.client_secret
      STORAGE_ORIGIN          = local.r2_endpoint # build time (CSP connect-src/media-src)
      } : {
      key       = key
      value     = value
      target    = ["production"]
      sensitive = contains(["KEYCLOAK_CLIENT_SECRET", "REDIS_URL", "CF_ACCESS_CLIENT_SECRET"], key)
    }
  ]
}

resource "vercel_project_domain" "app" {
  project_id = vercel_project.web.id
  domain     = local.app_host
}
