// Define the resources to create
// Provisions the following into GCP: 
//    Storage Bucket

// Storage Bucket
resource "google_storage_bucket" "se_bucket" {
  name     = var.bucket_name
  location = var.gcp_region

  labels = {
    name        = var.bucket_name
    owner       = var.bucket_owner
    environment = "se-demo"
  }
}
