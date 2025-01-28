//Version requirements or limitations 
//As well as location to define remote backend for storing state
terraform {

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}
