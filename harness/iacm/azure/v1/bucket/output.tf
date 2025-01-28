//Output After Run
output "bucket_id" {
  description = "Bucket ID"
  value       = azurerm_storage_blob.se_bucket.id
}
