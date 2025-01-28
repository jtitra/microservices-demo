// Define the resources to create
// Provisions the following resources: 
//    GKE Cluster, GKE Node Pool
locals {
  gke_cluster_id = replace(var.gke_cluster_name, "-", "_")
}

// GKE Cluster
resource "google_container_cluster" "gke_cluster" {
  name     = var.gke_cluster_name
  location = var.gcp_zone

  deletion_protection      = false
  remove_default_node_pool = true
  initial_node_count       = 1

  network    = "sandbox-testing"
  subnetwork = "sandbox-testing"

  master_auth {
    client_certificate_config {
      issue_client_certificate = true
    }
  }

  workload_identity_config {
    workload_pool = "${var.gcp_project_id}.svc.id.goog"
  }

  resource_labels = {
    env     = var.gke_cluster_name
    purpose = var.resource_purpose
    owner   = var.resource_owner
  }

  timeouts {
    create = "60m"
    update = "60m"
  }
}

// GKE Node Pool
resource "google_container_node_pool" "gke_node_pool" {
  name       = "${google_container_cluster.gke_cluster.name}-pool-01"
  cluster    = google_container_cluster.gke_cluster.id
  node_count = var.gke_min_node_count

  autoscaling {
    min_node_count = var.gke_min_node_count
    max_node_count = var.gke_max_node_count
  }

  management {
    auto_upgrade = true
  }

  node_config {
    machine_type = var.gke_machine_type
    oauth_scopes = ["https://www.googleapis.com/auth/cloud-platform"]

    metadata = {
      disable-legacy-endpoints = "true"
    }

    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }

  timeouts {
    create = "60m"
    update = "60m"
  }
}

// Harness K8s Connector
resource "harness_platform_connector_kubernetes" "gke_k8s" {
  identifier = "gke_${local.gke_cluster_id}"
  name       = "GKE - ${var.gke_cluster_name}"
  org_id     = var.org_id
  project_id = var.project_id

  client_key_cert {
    master_url           = "https://${google_container_cluster.gke_cluster.endpoint}"
    client_cert_ref      = harness_platform_secret_text.client_cert.id
    client_key_ref       = harness_platform_secret_text.client_key.id
    client_key_algorithm = "RSA"
  }

  tags = ["owner:${var.resource_owner}", "iacmManaged:true"]
}

// Harness Secrets
resource "harness_platform_secret_text" "client_cert" {
  identifier = "gke_client_cert_${local.gke_cluster_id}"
  name       = "GKE Client Cert - ${var.gke_cluster_name}"
  org_id     = var.org_id
  project_id = var.project_id

  secret_manager_identifier = "account.harnessSecretManager"
  value_type                = "Inline"
  value                     = google_container_cluster.gke_cluster.master_auth.0.client_certificate

  tags = ["owner:${var.resource_owner}", "iacmManaged:true"]
}

resource "harness_platform_secret_text" "client_key" {
  identifier = "gke_client_key_${local.gke_cluster_id}"
  name       = "GKE Client Key - ${var.gke_cluster_name}"
  org_id     = var.org_id
  project_id = var.project_id

  secret_manager_identifier = "account.harnessSecretManager"
  value_type                = "Inline"
  value                     = google_container_cluster.gke_cluster.master_auth.0.client_key

  tags = ["owner:${var.resource_owner}", "iacmManaged:true"]
}
