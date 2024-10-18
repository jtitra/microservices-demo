// Define Valid Variables

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

// Harness Config
variable "namespace" {
  type = string
}
