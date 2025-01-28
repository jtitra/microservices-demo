//Define Valid Variables

// GCP Environment
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

// Storage Bucket
variable "bucket_name" {
  type = string
}

variable "bucket_owner" {
  type = string
}
