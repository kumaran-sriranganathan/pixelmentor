###############################################################################
# Module: storage — Blob Storage for photo uploads
###############################################################################

variable "suffix"              {}
variable "environment"         {}
variable "location"            {}
variable "resource_group_name" {}
variable "key_vault_id"        {}
variable "subnet_id"           {}
variable "private_dns_zone_id" {}
variable "tags"                {}

resource "azurerm_storage_account" "main" {
  name                            = "pm${var.environment}st${var.suffix}"
  location                        = var.location
  resource_group_name             = var.resource_group_name
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  account_kind                    = "StorageV2"
  public_network_access_enabled   = true
  allow_nested_items_to_be_public = false
  min_tls_version                 = "TLS1_2"
  shared_access_key_enabled       = true

  blob_properties {
    delete_retention_policy {
      days = 7
    }
    container_delete_retention_policy {
      days = 7
    }
    versioning_enabled = true
  }

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

resource "azurerm_storage_container" "photos" {
  name                  = "user-photos"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

resource "azurerm_storage_container" "lessons" {
  name                  = "lesson-assets"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

resource "azurerm_storage_container" "processed" {
  name                  = "processed-photos"
  storage_account_name  = azurerm_storage_account.main.name
  container_access_type = "private"
}

resource "azurerm_storage_management_policy" "photos_lifecycle" {
  storage_account_id = azurerm_storage_account.main.id

  rule {
    name    = "delete-old-processed-photos"
    enabled = true
    filters {
      prefix_match = ["processed-photos/"]
      blob_types   = ["blockBlob"]
    }
    actions {
      base_blob {
        tier_to_cool_after_days_since_modification_greater_than    = 30
        tier_to_archive_after_days_since_modification_greater_than = 90
        delete_after_days_since_modification_greater_than          = 365
      }
    }
  }
}

# Outputs — connection string output directly
output "account_name"               { value = azurerm_storage_account.main.name }
output "account_id"                 { value = azurerm_storage_account.main.id }
output "primary_blob_endpoint"      { value = azurerm_storage_account.main.primary_blob_endpoint }
output "primary_connection_string"  {
  value     = azurerm_storage_account.main.primary_connection_string
  sensitive = true
}
