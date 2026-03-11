###############################################################################
# Module: resource_group
###############################################################################

resource "azurerm_resource_group" "main" {
  name     = "pixelmentor-${var.environment}-rg"
  location = var.location
  tags     = var.tags
}

variable "suffix"      {}
variable "environment" {}
variable "location"    {}
variable "tags"        {}

output "name"     { value = azurerm_resource_group.main.name }
output "location" { value = azurerm_resource_group.main.location }
