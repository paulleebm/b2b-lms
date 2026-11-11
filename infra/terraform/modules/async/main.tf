# Asynchronous work (docs/05 §6-1, §10):
#   lms-events      ← outbox relay publishes domain events (certificates, mails); worker consumes
#   lms-transcode   ← EventBridge forwards MediaConvert job COMPLETE/ERROR; worker updates video_asset
# Both have a dead-letter queue: a message that fails 5 times is parked, not retried forever,
# and an alarm fires (observability module).

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_sqs_queue" "events_dlq" {
  name                      = "${var.name}-events-dlq"
  message_retention_seconds = 1209600 # 14 days to look at what failed
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "events" {
  name                       = "${var.name}-events"
  visibility_timeout_seconds = 120 # > the slowest handler (PDF generation)
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.events_dlq.arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue" "transcode_dlq" {
  name                      = "${var.name}-transcode-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "transcode" {
  name                       = "${var.name}-transcode"
  visibility_timeout_seconds = 60
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transcode_dlq.arn
    maxReceiveCount     = 5
  })
}

# --- MediaConvert -----------------------------------------------------------------------------
# Job settings (HLS 1080p/720p/480p + thumbnail) are sent by the API with each CreateJob call
# (backend: MediaConvertTranscoder); the Terraform AWS provider manages queues and roles, not
# job templates.

resource "aws_media_convert_queue" "this" {
  name         = var.name
  pricing_plan = "ON_DEMAND"
}

data "aws_iam_policy_document" "mediaconvert_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["mediaconvert.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "mediaconvert" {
  name               = "${var.name}-mediaconvert"
  assume_role_policy = data.aws_iam_policy_document.mediaconvert_trust.json
}

data "aws_iam_policy_document" "mediaconvert" {
  statement {
    sid       = "ReadUploadedSources"
    actions   = ["s3:GetObject"]
    resources = ["${var.media_bucket_arn}/tenants/*/files/*"]
  }
  statement {
    sid       = "WriteRenditions"
    actions   = ["s3:PutObject"]
    resources = ["${var.media_bucket_arn}/tenants/*/videos/*"]
  }
}

resource "aws_iam_role_policy" "mediaconvert" {
  name   = "s3"
  role   = aws_iam_role.mediaconvert.id
  policy = data.aws_iam_policy_document.mediaconvert.json
}

# Completion is an event, not something we poll for.
resource "aws_cloudwatch_event_rule" "transcode_finished" {
  name        = "${var.name}-transcode-finished"
  description = "MediaConvert jobs of our queue that completed or failed"
  event_pattern = jsonencode({
    source        = ["aws.mediaconvert"]
    "detail-type" = ["MediaConvert Job State Change"]
    detail = {
      status = ["COMPLETE", "ERROR"]
      queue  = [aws_media_convert_queue.this.arn]
    }
  })
}

resource "aws_cloudwatch_event_target" "transcode_finished" {
  rule = aws_cloudwatch_event_rule.transcode_finished.name
  arn  = aws_sqs_queue.transcode.arn
}

data "aws_iam_policy_document" "transcode_queue" {
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.transcode.arn]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_cloudwatch_event_rule.transcode_finished.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "transcode" {
  queue_url = aws_sqs_queue.transcode.id
  policy    = data.aws_iam_policy_document.transcode_queue.json
}
