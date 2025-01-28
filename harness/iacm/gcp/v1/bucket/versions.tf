//Version requirements or limitations 
//As well as location to define remote backend for storing state
terraform {

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}
