#!/usr/bin/env bash
# =============================================================================
# PixelMentor — Terraform Import Script
# Imports all existing Azure resources into Terraform state
# Run from the terraform/ directory
# =============================================================================

#!/usr/bin/env bash
# =============================================================================
# PixelMentor — Terraform Import Script
# Imports all existing Azure resources into Terraform state
# Run from the terraform/ directory
# =============================================================================

set -euo pipefail

SUB="/subscriptions/76798183-b853-41c3-80bd-d143b56985c3"
RG="${SUB}/resourceGroups/pixelmentor-dev-rg"

echo "🔧 Importing existing Azure resources into Terraform state..."
echo ""

# Resource Group (already imported, will skip if exists)
echo "📦 Resource Group..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.resource_group.azurerm_resource_group.main \
  "${RG}" 2>/dev/null && echo "✅ resource_group" || echo "ℹ️  resource_group already in state, skipping"

# User Assigned Identity
echo "📦 Managed Identity..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.security.azurerm_user_assigned_identity.app \
  "${RG}/providers/Microsoft.ManagedIdentity/userAssignedIdentities/pixelmentor-dev-identity"

# Key Vault
echo "📦 Key Vault..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.security.azurerm_key_vault.main \
  "${RG}/providers/Microsoft.KeyVault/vaults/pm-dev-kv-dev01"

# Container Registry
echo "📦 Container Registry..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.security.azurerm_container_registry.main \
  "${RG}/providers/Microsoft.ContainerRegistry/registries/pmdevacrdev01"

# Log Analytics Workspace
echo "📦 Log Analytics Workspace..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.monitoring.azurerm_log_analytics_workspace.main \
  "${RG}/providers/Microsoft.OperationalInsights/workspaces/pm-dev-law-dev01"

# Application Insights
echo "📦 Application Insights..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.monitoring.azurerm_application_insights.main \
  "${RG}/providers/Microsoft.Insights/components/pm-dev-appinsights-dev01"

# Action Group
echo "📦 Action Group..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.monitoring.azurerm_monitor_action_group.main \
  "${RG}/providers/Microsoft.Insights/actionGroups/pm-dev-alerts"

# Metric Alert
echo "📦 Metric Alert..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.monitoring.azurerm_monitor_metric_alert.high_error_rate \
  "${RG}/providers/Microsoft.Insights/metricAlerts/pm-dev-high-error-rate"

# Storage Account
echo "📦 Storage Account..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.storage.azurerm_storage_account.main \
  "${RG}/providers/Microsoft.Storage/storageAccounts/pmdevstdev01"

# Cosmos DB
echo "📦 Cosmos DB Account..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.cosmos.azurerm_cosmosdb_account.main \
  "${RG}/providers/Microsoft.DocumentDB/databaseAccounts/pm-dev-cosmos-dev01"

# OpenAI
echo "📦 OpenAI..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.openai.azurerm_cognitive_account.openai \
  "${RG}/providers/Microsoft.CognitiveServices/accounts/pm-dev-openai-dev01"

# AI Search
echo "📦 AI Search..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.search.azurerm_search_service.main \
  "${RG}/providers/Microsoft.Search/searchServices/pm-dev-search-dev01"

# Virtual Network
echo "📦 Virtual Network..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.networking.azurerm_virtual_network.main \
  "${RG}/providers/Microsoft.Network/virtualNetworks/pixelmentor-dev-vnet"

# Network Security Group
echo "📦 Network Security Group..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.networking.azurerm_network_security_group.private_endpoints \
  "${RG}/providers/Microsoft.Network/networkSecurityGroups/pixelmentor-dev-pe-nsg"

# Private DNS Zones
echo "📦 Private DNS Zones..."
MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone.zones["acr"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.azurecr.io"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone.zones["blob"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.blob.core.windows.net"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone.zones["cosmos"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.documents.azure.com"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone.zones["openai"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.openai.azure.com"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone.zones["search"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.search.windows.net"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone.zones["vault"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.vaultcore.azure.net"

# Private DNS Zone VNet Links
echo "📦 Private DNS Zone VNet Links..."
MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone_virtual_network_link.links["acr"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.azurecr.io/virtualNetworkLinks/acr-vnet-link"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone_virtual_network_link.links["blob"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.blob.core.windows.net/virtualNetworkLinks/blob-vnet-link"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone_virtual_network_link.links["cosmos"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.documents.azure.com/virtualNetworkLinks/cosmos-vnet-link"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone_virtual_network_link.links["openai"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.openai.azure.com/virtualNetworkLinks/openai-vnet-link"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone_virtual_network_link.links["search"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.search.windows.net/virtualNetworkLinks/search-vnet-link"

MSYS_NO_PATHCONV=1 terraform import -var-file="dev.tfvars" \
  'module.networking.azurerm_private_dns_zone_virtual_network_link.links["vault"]' \
  "${RG}/providers/Microsoft.Network/privateDnsZones/privatelink.vaultcore.azure.net/virtualNetworkLinks/vault-vnet-link"

# Container App Environment
echo "📦 Container App Environment..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.container_apps.azurerm_container_app_environment.main \
  "${RG}/providers/Microsoft.App/managedEnvironments/pm-dev-cae-dev01"

# Container App
echo "📦 Container App..."
MSYS_NO_PATHCONV=1 terraform import \
  -var-file="dev.tfvars" \
  module.container_apps.azurerm_container_app.api \
  "${RG}/providers/Microsoft.App/containerApps/pm-dev-api"

echo ""
echo "✅ All imports complete! Run 'terraform plan -var-file=dev.tfvars' to verify."