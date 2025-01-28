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

  dynamic "terraform_variable" {
    for_each = var.tf_vars

    content {
      key        = terraform_variable.value.key
      value      = terraform_variable.value.value
      value_type = terraform_variable.value.value_type
    }
  }

  terraform_variable_file {
    repository           = var.tf_var_file.name
    repository_branch    = var.tf_var_file.branch
    repository_path      = var.tf_var_file.path
    repository_connector = var.tf_var_file.conn
  }
}
