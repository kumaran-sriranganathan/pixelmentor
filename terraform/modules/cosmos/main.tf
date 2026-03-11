###############################################################################
# Module: cosmos — Cosmos DB (NoSQL, serverless)
###############################################################################

variable "suffix"              {}
variable "environment"         {}
variable "location"            {}
variable "resource_group_name" {}
variable "key_vault_id"        {}
variable "subnet_id"           {}
variable "private_dns_zone_id" {}
variable "tags"                {}

resource "azurerm_cosmosdb_account" "main" {
  name                               = "pm-${var.environment}-cosmos-${var.suffix}"
  location                           = var.location
  resource_group_name                = var.resource_group_name
  offer_type                         = "Standard"
  kind                               = "GlobalDocumentDB"
  public_network_access_enabled      = true
  access_key_metadata_writes_enabled = false

  consistency_policy {
    consistency_level       = "Session"
    max_interval_in_seconds = 5
    max_staleness_prefix    = 100
  }

  geo_location {
    location          = var.location
    failover_priority = 0
  }

  capabilities {
    name = "EnableServerless"
  }

  backup {
    type                = "Periodic"
    interval_in_minutes = 240
    retention_in_hours  = 720
    storage_redundancy  = "Local"
  }

  tags = var.tags
}

resource "azurerm_cosmosdb_sql_database" "main" {
  name                = "pixelmentor"
  resource_group_name = var.resource_group_name
  account_name        = azurerm_cosmosdb_account.main.name
}

resource "azurerm_cosmosdb_sql_container" "users" {
  name                = "users"
  resource_group_name = var.resource_group_name
  account_name        = azurerm_cosmosdb_account.main.name
  database_name       = azurerm_cosmosdb_sql_database.main.name
  partition_key_paths = ["/userId"]

  indexing_policy {
    indexing_mode = "consistent"
    included_path { path = "/*" }
    excluded_path { path = "/_etag/?" }
  }
}

resource "azurerm_cosmosdb_sql_container" "photo_analyses" {
  name                = "photoAnalyses"
  resource_group_name = var.resource_group_name
  account_name        = azurerm_cosmosdb_account.main.name
  database_name       = azurerm_cosmosdb_sql_database.main.name
  partition_key_paths = ["/userId"]
  default_ttl         = 7776000
}

resource "azurerm_cosmosdb_sql_container" "chat_history" {
  name                = "chatHistory"
  resource_group_name = var.resource_group_name
  account_name        = azurerm_cosmosdb_account.main.name
  database_name       = azurerm_cosmosdb_sql_database.main.name
  partition_key_paths = ["/userId"]
  default_ttl         = 2592000
}

resource "azurerm_cosmosdb_sql_container" "skill_profiles" {
  name                = "skillProfiles"
  resource_group_name = var.resource_group_name
  account_name        = azurerm_cosmosdb_account.main.name
  database_name       = azurerm_cosmosdb_sql_database.main.name
  partition_key_paths = ["/userId"]
}

# Outputs — connection string output so container_apps can use it directly
output "endpoint"               { value = azurerm_cosmosdb_account.main.endpoint }
output "account_name"           { value = azurerm_cosmosdb_account.main.name }
output "primary_connection_string" {
  value     = azurerm_cosmosdb_account.main.primary_sql_connection_string
  sensitive = true
}
