//Output After Run
output "bucket_id" {
  description = "Bucket ID"
  value       = google_storage_bucket.se_bucket.self_link
}
