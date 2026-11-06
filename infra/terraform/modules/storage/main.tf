# Media: one private bucket, browser uploads straight to it (presigned URLs), learners stream HLS
# through CloudFront with signed cookies (docs/05 §8).
#
# Object layout (keys never contain client file names):
#   tenants/{tenant}/files/{id}/source     ← uploads (presigned PUT / multipart). Never served by the CDN.
#   tenants/{tenant}/videos/{id}/hls/…     ← MediaConvert output, served by CloudFront to signed-cookie holders
#   tenants/{tenant}/certificates/{id}.pdf ← Day 4, downloaded via presigned GET

terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.us_east_1]
    }
  }
}

resource "aws_s3_bucket" "media" {
  bucket = var.bucket_name
}

resource "aws_s3_bucket_ownership_controls" "media" {
  bucket = aws_s3_bucket.media.id
  rule {
    object_ownership = "BucketOwnerEnforced" # no ACLs: access is decided by policies only
  }
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket                  = aws_s3_bucket.media.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# The browser PUTs directly: allow our app origin only, and expose ETag (multipart needs it).
resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  cors_rule {
    allowed_origins = [var.app_origin]
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  rule {
    id     = "abort-abandoned-multipart"
    status = "Enabled"
    filter {}
    abort_incomplete_multipart_upload {
      days_after_initiation = 1 # parts of abandoned uploads are billed until aborted
    }
  }
}

# --- CloudFront: HLS only, signed cookies --------------------------------------------------------

resource "aws_cloudfront_origin_access_control" "media" {
  name                              = "${var.name}-media"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# The API signs cookies with the private key (kept in SSM); CloudFront trusts this public key.
resource "aws_cloudfront_public_key" "signer" {
  name        = "${var.name}-signer"
  encoded_key = var.cloudfront_public_key_pem
}

resource "aws_cloudfront_key_group" "signer" {
  name  = "${var.name}-signer"
  items = [aws_cloudfront_public_key.signer.id]
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_response_headers_policy" "media_cors" {
  name = "${var.name}-media-cors"
  cors_config {
    access_control_allow_credentials = true # hls.js sends the signed cookies (withCredentials)
    access_control_allow_headers {
      items = ["*"]
    }
    access_control_allow_methods {
      items = ["GET", "HEAD"]
    }
    access_control_allow_origins {
      items = [var.app_origin]
    }
    origin_override = true
  }
}

resource "aws_acm_certificate" "media" {
  provider          = aws.us_east_1 # CloudFront only accepts certificates from us-east-1
  domain_name       = var.media_host
  validation_method = "DNS"
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "media_validation" {
  for_each = { for o in aws_acm_certificate.media.domain_validation_options : o.domain_name => o }
  zone_id  = var.zone_id
  name     = each.value.resource_record_name
  type     = each.value.resource_record_type
  records  = [each.value.resource_record_value]
  ttl      = 300
}

resource "aws_acm_certificate_validation" "media" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.media.arn
  validation_record_fqdns = [for r in aws_route53_record.media_validation : r.fqdn]
}

resource "aws_cloudfront_distribution" "media" {
  enabled         = true
  comment         = "${var.name} HLS video"
  aliases         = [var.media_host] # same parent domain as the app, so the BFF can set the cookies
  price_class     = "PriceClass_200" # includes Korea/Japan edges, cheaper than All
  is_ipv6_enabled = true

  origin {
    origin_id                = "media"
    domain_name              = aws_s3_bucket.media.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.media.id
  }

  # Nothing is served by default…
  default_cache_behavior {
    target_origin_id       = "media"
    viewer_protocol_policy = "https-only"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    trusted_key_groups     = [aws_cloudfront_key_group.signer.id]
  }

  # …video renditions only, and only with a valid signed cookie (path-scoped per video).
  ordered_cache_behavior {
    path_pattern               = "/tenants/*/videos/*"
    target_origin_id           = "media"
    viewer_protocol_policy     = "https-only"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.media_cors.id
    trusted_key_groups         = [aws_cloudfront_key_group.signer.id]
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.media.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# CloudFront may read rendition objects only; raw uploads and certificates stay private.
data "aws_iam_policy_document" "media" {
  statement {
    sid       = "CloudFrontReadsVideoRenditions"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.media.arn}/tenants/*/videos/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.media.arn]
    }
  }
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    resources = [aws_s3_bucket.media.arn, "${aws_s3_bucket.media.arn}/*"]
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "media" {
  bucket     = aws_s3_bucket.media.id
  policy     = data.aws_iam_policy_document.media.json
  depends_on = [aws_s3_bucket_public_access_block.media]
}

resource "aws_route53_record" "media" {
  zone_id = var.zone_id
  name    = var.media_host
  type    = "A"
  alias {
    name                   = aws_cloudfront_distribution.media.domain_name
    zone_id                = aws_cloudfront_distribution.media.hosted_zone_id
    evaluate_target_health = false
  }
}
