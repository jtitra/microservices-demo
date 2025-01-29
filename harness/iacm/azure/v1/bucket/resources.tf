// Define the resources to create
// Provisions the following into Azure: 
//    Blob Storage Container

// Blob Storage Container
resource "azurerm_storage_container" "se_bucket" {
  name                  = var.bucket_name
  storage_account_name  = "harnessidpdemo"
  container_access_type = "private"

  metadata = {
    owner       = var.bucket_owner
    environment = "se-demo"
  }
}
