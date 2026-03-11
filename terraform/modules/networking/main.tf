###############################################################################
# Module: networking
# Creates: VNet, subnets for Container Apps & private endpoints,
#          private DNS zones for all PaaS services
###############################################################################

variable "suffix"              {}
variable "environment"         {}
variable "location"            {}
variable "resource_group_name" {}
variable "tags"                {}

# ── Virtual Network ──────────────────────────────────────────────────────────
resource "azurerm_virtual_network" "main" {
  name                = "pixelmentor-${var.environment}-vnet"
  location            = var.location
  resource_group_name = var.resource_group_name
  address_space       = ["10.0.0.0/16"]
  tags                = var.tags
}

# ── Subnets ──────────────────────────────────────────────────────────────────

# Container Apps environment subnet (needs /23 minimum)
resource "azurerm_subnet" "container_apps" {
  name                 = "container-apps-subnet"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.0.0/23"]

  delegation {
    name = "container-apps-delegation"
    service_delegation {
      name    = "Microsoft.App/environments"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

# Private endpoint subnet (no delegation)
resource "azurerm_subnet" "private_endpoints" {
  name                                      = "private-endpoints-subnet"
  resource_group_name                       = var.resource_group_name
  virtual_network_name                      = azurerm_virtual_network.main.name
  address_prefixes                          = ["10.0.4.0/24"]
  private_endpoint_network_policies = "Disabled"
}

# ── Network Security Group for private endpoints ──────────────────────────────
resource "azurerm_network_security_group" "private_endpoints" {
  name                = "pixelmentor-${var.environment}-pe-nsg"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.tags

  security_rule {
    name                       = "DenyAllInbound"
    priority                   = 4096
    direction                  = "Inbound"
    access                     = "Deny"
    protocol                   = "*"
    source_port_range          = "*"
    destination_port_range     = "*"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "private_endpoints" {
  subnet_id                 = azurerm_subnet.private_endpoints.id
  network_security_group_id = azurerm_network_security_group.private_endpoints.id
}

# ── Private DNS Zones ─────────────────────────────────────────────────────────

locals {
  dns_zones = {
    openai = "privatelink.openai.azure.com"
    blob   = "privatelink.blob.core.windows.net"
    cosmos = "privatelink.documents.azure.com"
    search = "privatelink.search.windows.net"
    vault  = "privatelink.vaultcore.azure.net"
    acr    = "privatelink.azurecr.io"
  }
}

resource "azurerm_private_dns_zone" "zones" {
  for_each            = local.dns_zones
  name                = each.value
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "links" {
  for_each              = local.dns_zones
  name                  = "${each.key}-vnet-link"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.zones[each.key].name
  virtual_network_id    = azurerm_virtual_network.main.id
  registration_enabled  = false
  tags                  = var.tags
}

# ── Outputs ───────────────────────────────────────────────────────────────────
output "vnet_id"                      { value = azurerm_virtual_network.main.id }
output "container_apps_subnet_id"     { value = azurerm_subnet.container_apps.id }
output "private_endpoint_subnet_id"   { value = azurerm_subnet.private_endpoints.id }
output "openai_private_dns_zone_id"   { value = azurerm_private_dns_zone.zones["openai"].id }
output "blob_private_dns_zone_id"     { value = azurerm_private_dns_zone.zones["blob"].id }
output "cosmos_private_dns_zone_id"   { value = azurerm_private_dns_zone.zones["cosmos"].id }
output "search_private_dns_zone_id"   { value = azurerm_private_dns_zone.zones["search"].id }
output "vault_private_dns_zone_id"    { value = azurerm_private_dns_zone.zones["vault"].id }
output "acr_private_dns_zone_id"      { value = azurerm_private_dns_zone.zones["acr"].id }
