###############################################################################
# Module: openai — Azure OpenAI + GPT-4o + text-embedding
# DALL-E 3 removed — not available in australiaeast
###############################################################################

variable "suffix"              {}
variable "environment"         {}
variable "location"            {}
variable "resource_group_name" {}
variable "key_vault_id"        {}
variable "subnet_id"           {}
variable "private_dns_zone_id" {}
variable "tags"                {}
variable "gpt4o_capacity"      { default = 20 }

resource "azurerm_cognitive_account" "openai" {
  name                          = "pm-${var.environment}-openai-${var.suffix}"
  location                      = var.location
  resource_group_name           = var.resource_group_name
  kind                          = "OpenAI"
  sku_name                      = "S0"
  custom_subdomain_name         = "pm-${var.environment}-openai-${var.suffix}"
  public_network_access_enabled = true

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

resource "azurerm_cognitive_deployment" "gpt4o" {
  name                 = "gpt-4o"
  cognitive_account_id = azurerm_cognitive_account.openai.id

  model {
    format  = "OpenAI"
    name    = "gpt-4o"
    version = "2024-11-20"
  }

  scale {
    type     = "Standard"
    capacity = var.gpt4o_capacity
  }
}

resource "azurerm_cognitive_deployment" "embedding" {
  name                 = "text-embedding-3-large"
  cognitive_account_id = azurerm_cognitive_account.openai.id

  model {
    format  = "OpenAI"
    name    = "text-embedding-3-large"
    version = "1"
  }

  scale {
    type     = "Standard"
    capacity = 20
  }
}

# Outputs — keys output directly, no Key Vault dependency
output "endpoint"    { value = azurerm_cognitive_account.openai.endpoint }
output "account_id"  { value = azurerm_cognitive_account.openai.id }
output "primary_key" {
  value     = azurerm_cognitive_account.openai.primary_access_key
  sensitive = true
}
