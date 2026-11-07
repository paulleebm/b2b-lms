variable "domain" {
  description = "Domain bought at Gabia; its nameservers are switched to the ones output by this stack"
  type        = string
  default     = "bymile.site"
}

variable "cloudflare_account_id" {
  description = "Cloudflare dashboard → any page → right sidebar \"Account ID\""
  type        = string
}

variable "admin_email" {
  description = "The only address Cloudflare Access lets into the Keycloak admin console and the mail inbox (one-time code by email)"
  type        = string
}

variable "github_repo" {
  description = "owner/name of the repository Vercel builds"
  type        = string
  default     = "paulleebm/b2b-lms"
}

variable "production_branch" {
  description = "Vercel deploys this branch to app.<domain>"
  type        = string
  default     = "main"
}

variable "image_prefix" {
  description = "Docker Hub namespace the home images are pushed to, e.g. docker.io/yourname"
  type        = string
}

variable "upstash_email" {
  description = "Upstash account email (the API key comes from TF_VAR_upstash_api_key)"
  type        = string
}

variable "upstash_api_key" {
  description = "Upstash console → Account → Management API → Create API key. Pass as TF_VAR_upstash_api_key, never in a file"
  type        = string
  sensitive   = true
}

variable "upstash_region" {
  description = "Primary region of the session store; Upstash has no Seoul region, Tokyo is the closest"
  type        = string
  default     = "ap-northeast-1"
}
