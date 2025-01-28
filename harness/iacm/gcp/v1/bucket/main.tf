//Define the provider and any data sources
provider "google" {
  project = var.gcp_project_id
  region  = var.gcp_region
}
