// Define the resources to create
// Provisions the following into Harness: 
//    IaCM Workspace

// IaCM Workspace
resource "harness_platform_workspace" "se_workspace" {
  name                    = var.workspace_name
  identifier              = var.workspace_name
  org_id                  = var.org_id
  project_id              = var.project_id
  provisioner_type        = var.workspace_provisioner_type
  provisioner_version     = var.workspace_provisioner_version
  repository              = var.workspace_repository_name
  repository_branch       = var.workspace_repository_branch
  repository_path         = var.workspace_repository_path
  cost_estimation_enabled = true
  provider_connector      = var.workspace_provider_connector
  repository_connector    = var.workspace_repository_connector

  terraform_variable {
    key        = "bucket_name"
    value      = var.bucket_name
    value_type = "string"
  }
  terraform_variable {
    key        = "bucket_owner"
    value      = var.bucket_owner
    value_type = "string"
  }
}
