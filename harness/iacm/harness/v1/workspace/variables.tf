// Define Valid Variables

// Platform
variable "account_id" {
  type = string
}

variable "org_id" {
  type = string
}

variable "project_id" {
  type = string
}

variable "api_key" {
  type      = string
  sensitive = true
}

// Workspace
variable "workspace_name" {
  type = string
}

variable "workspace_provisioner_type" {
  type    = string
  default = "terraform"
}

variable "workspace_provisioner_version" {
  type    = string
  default = "1.5.6"
}

variable "workspace_repository_name" {
  type = string
}

variable "workspace_repository_branch" {
  type    = string
  default = "main"
}

variable "workspace_repository_path" {
  type = string
}

variable "workspace_provider_connector" {
  type = string
}

variable "bucket_name" {
  type = string
}

variable "bucket_owner" {
  type = string
}
