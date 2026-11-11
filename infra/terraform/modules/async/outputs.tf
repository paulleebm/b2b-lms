output "events_queue_arn" {
  value = aws_sqs_queue.events.arn
}

output "events_queue_url" {
  value = aws_sqs_queue.events.id
}

output "transcode_queue_arn" {
  value = aws_sqs_queue.transcode.arn
}

output "transcode_queue_url" {
  value = aws_sqs_queue.transcode.id
}

output "dlq_names" {
  value = [aws_sqs_queue.events_dlq.name, aws_sqs_queue.transcode_dlq.name]
}

output "mediaconvert_queue_arn" {
  value = aws_media_convert_queue.this.arn
}

output "mediaconvert_role_arn" {
  value = aws_iam_role.mediaconvert.arn
}

output "events_queue_name" {
  value = aws_sqs_queue.events.name
}

output "transcode_queue_name" {
  value = aws_sqs_queue.transcode.name
}
