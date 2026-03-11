###############################################################################
# Module: security
# Creates: Key Vault, Azure Container Registry, User-Assigned Managed Identity
# Fix: Added time_sleep after role assignments so RBAC propagates before
#      other modules try to write secrets to Key Vault
###############################################################################

variable "suffix"              {}
variable "environment"         {}
variable "location"            {}
variable "resource_group_name" {}
variable "tenant_id"           {}
variable "subnet_id"           {}
variable "tags"                {}

data "azurerm_client_config" "current" {}

# ── User-Assigned Managed Identity ───────────────────────────────────────────
resource "azurerm_user_assigned_identity" "app" {
  name                = "pixelmentor-${var.environment}-identity"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

# ── Azure Key Vault ───────────────────────────────────────────────────────────
resource "azurerm_key_vault" "main" {
  name                          = "pm-${var.environment}-kv-${var.suffix}"
  location                      = var.location
  resource_group_name           = var.resource_group_name
  tenant_id                     = var.tenant_id
  sku_name                      = "standard"
  enable_rbac_authorization     = true
  soft_delete_retention_days    = 7
  purge_protection_enabled      = false
  public_network_access_enabled = true

  network_acls {
    default_action = "Allow"
    bypass         = "AzureServices"
  }

  tags = var.tags
}

# Grant the app identity permission to read secrets
resource "azurerm_role_assignment" "app_keyvault_secrets" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.app.principal_id
}

# Grant Terraform executor permission to manage secrets
resource "azurerm_role_assignment" "terraform_keyvault_admin" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Administrator"
  principal_id         = data.azurerm_client_config.current.object_id
}

# ── CRITICAL: Wait for RBAC to propagate before other modules write secrets ───
# Azure RBAC assignments take 2-5 minutes to activate after creation.
# Without this delay, Terraform gets 403 Forbidden when writing secrets.
resource "time_sleep" "wait_for_rbac" {
  depends_on = [
    azurerm_role_assignment.terraform_keyvault_admin,
    azurerm_role_assignment.app_keyvault_secrets,
  ]
  create_duration = "120s"   # Wait 2 minutes for RBAC to propagate
}

# ── Azure Container Registry ──────────────────────────────────────────────────
resource "azurerm_container_registry" "main" {
  name                          = "pm${var.environment}acr${var.suffix}"
  location                      = var.location
  resource_group_name           = var.resource_group_name
  sku                           = "Basic"
  admin_enabled                 = false
  public_network_access_enabled = true

  tags = var.tags
}

# Allow the app identity to pull images from ACR
resource "azurerm_role_assignment" "app_acr_pull" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.app.principal_id
}

# ── Outputs ───────────────────────────────────────────────────────────────────
output "key_vault_id"          { value = azurerm_key_vault.main.id }
output "key_vault_uri"         { value = azurerm_key_vault.main.vault_uri }
output "rbac_ready"            { value = time_sleep.wait_for_rbac.id }
output "identity_id"           { value = azurerm_user_assigned_identity.app.id }
output "identity_principal_id" { value = azurerm_user_assigned_identity.app.principal_id }
output "identity_client_id"    { value = azurerm_user_assigned_identity.app.client_id }
output "acr_login_server"      { value = azurerm_container_registry.main.login_server }
output "acr_id"                { value = azurerm_container_registry.main.id }
