output "bucket_name" {
  value = aws_s3_bucket.media.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.media.arn
}

output "cloudfront_key_pair_id" {
  description = "Public key id the API puts in the signed cookies"
  value       = aws_cloudfront_public_key.signer.id
}

output "media_base_url" {
  value = "https://${var.media_host}"
}
