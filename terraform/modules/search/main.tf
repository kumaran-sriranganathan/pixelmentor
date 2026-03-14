###############################################################################
# Module: search — Azure AI Search for RAG lesson recommendations
###############################################################################
variable "suffix"              {}
variable "environment"         {}
variable "location"            {}
variable "resource_group_name" {}
variable "key_vault_id"        {}
variable "subnet_id"           {}
variable "private_dns_zone_id" {}
variable "tags"                {}
variable "sku"                 { default = "basic" }

resource "azurerm_search_service" "main" {
  name                          = "pm-${var.environment}-search-${var.suffix}"
  location                      = var.location
  resource_group_name           = var.resource_group_name
  sku                           = var.sku
  replica_count                 = 1
  partition_count               = 1
  public_network_access_enabled = true
  local_authentication_enabled  = true
  tags                          = var.tags
}

# Outputs — keys output directly so container_apps can use them
output "endpoint"    { value = "https://${azurerm_search_service.main.name}.search.windows.net" }
output "id"          { value = azurerm_search_service.main.id }
output "primary_key" {
  value     = azurerm_search_service.main.primary_key
  sensitive = true
}
