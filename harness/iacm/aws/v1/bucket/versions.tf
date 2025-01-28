//Version requirements or limitations 
//As well as location to define remote backend for storing state
terraform {

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5"
    }
  }
}
