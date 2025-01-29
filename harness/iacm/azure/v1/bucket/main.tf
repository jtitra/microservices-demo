//Define the provider and any data sources
provider "azurerm" {
  features {}

  subscription_id = var.az_subscription_id
}
