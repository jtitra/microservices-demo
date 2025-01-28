//Output After Run
output "bucket_id" {
  description = "Bucket ID"
  value       = aws_s3_bucket.se_bucket.arn
}
