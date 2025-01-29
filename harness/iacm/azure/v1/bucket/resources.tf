// Define the resources to create
// Provisions the following into Azure: 
//    Blob Storage

// Blob Storage
resource "azurerm_storage_blob" "se_bucket" {
  name                   = var.bucket_name
  storage_account_name   = "harnessidpdemo"
  storage_container_name = "demo"
  type                   = "Block"

//  metadata = {
//    Name        = var.bucket_name
//    Owner       = var.bucket_owner
//    Environment = "se-demo"
//  }
}
