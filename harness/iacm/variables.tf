// Define Valid Variables
// Harness Platform
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

// GCP
variable "gcp_project_id" {
  type    = string
  default = "sales-209522"
}

variable "gcp_region" {
  type    = string
  default = "us-east1"
}

variable "gcp_zone" {
  type    = string
  default = "us-east1-b"
}

// GKE Cluster & Node Pool
variable "gke_cluster_name" {
  type = string
}

variable "gke_min_node_count" {
  type = string
}

variable "gke_max_node_count" {
  type = string
}

variable "gke_machine_type" {
  type = string
}

variable "resource_owner" {
  type = string
}

variable "resource_purpose" {
  type = string
}

// Harness Config
variable "namespace" {
  type = string
}
