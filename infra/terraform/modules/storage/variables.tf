variable "name" {
  type = string
}

variable "bucket_name" {
  description = "Globally unique media bucket name"
  type        = string
}

variable "app_origin" {
  description = "https://app.example.com — the only origin allowed to upload and to fetch video"
  type        = string
}

variable "media_host" {
  description = "media.example.com — CloudFront alias under the app's parent domain"
  type        = string
}

variable "zone_id" {
  type = string
}

variable "cloudfront_public_key_pem" {
  description = "Public half of the cookie-signing key pair (the private half goes to SSM, never to Terraform)"
  type        = string
}
