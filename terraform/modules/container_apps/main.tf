###############################################################################
# Module: container_apps
# Secrets passed as direct environment variables for dev simplicity.
# In prod: switch secret blocks back to Key Vault references.
###############################################################################

variable "suffix"                         {}
variable "environment"                    {}
variable "location"                       {}
variable "resource_group_name"            {}
variable "log_analytics_workspace_id"     {}
variable "app_insights_connection_string" {}
variable "acr_login_server"               {}
variable "acr_id"                         {}
variable "cosmos_endpoint"                {}
variable "cosmos_connection_string"       { sensitive = true }
variable "storage_account_name"           {}
variable "storage_connection_string"      { sensitive = true }
variable "openai_endpoint"                {}
variable "openai_api_key"                 { sensitive = true }
variable "search_endpoint"                {}
variable "search_key"                     { sensitive = true }
variable "container_image_tag"            { default = "latest" }
variable "identity_id"                    {}
variable "identity_client_id"             {}
variable "tags"                           {}

variable "entra_tenant_id" {
  sensitive = true
  default   = ""
}

variable "entra_api_client_id" {
  sensitive = true
  default   = ""
}

variable "entra_android_client_id" {
  sensitive = true
  default   = ""
}

resource "azurerm_container_app_environment" "main" {
  name                       = "pm-${var.environment}-cae-${var.suffix}"
  location                   = var.location
  resource_group_name        = var.resource_group_name
  log_analytics_workspace_id = var.log_analytics_workspace_id
  tags                       = var.tags
}

resource "azurerm_container_app" "api" {
  lifecycle {
    ignore_changes = [template[0].container[0].image]
  }

  name                         = "pm-${var.environment}-api"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"
  tags                         = var.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [var.identity_id]
  }

  registry {
    server   = var.acr_login_server
    identity = var.identity_id
  }

  # Secrets stored as Container App secrets (encrypted at rest by Azure)
  secret {
	name  = "entra-tenant-id"
	value = var.entra_tenant_id
  }
  secret {
	name  = "entra-api-client-id"
	value = var.entra_api_client_id
  }
  secret {
    name  = "entra-android-client-id"
    value = var.entra_android_client_id
  }
  secret {
    name  = "openai-api-key"
    value = var.openai_api_key
  }
  secret {
    name  = "cosmos-connection-string"
    value = var.cosmos_connection_string
  }
  secret {
    name  = "storage-connection-string"
    value = var.storage_connection_string
  }
  secret {
    name  = "search-admin-key"
    value = var.search_key
  }
  secret {
	name  = "entra-tenant-id"
	value = var.entra_tenant_id
  }
  secret {
	name  = "entra-api-client-id"
	value = var.entra_api_client_id
  }
  secret {
	name  = "entra-android-client-id"
	value = var.entra_android_client_id
  }

  template {
    min_replicas = 1
    max_replicas = var.environment == "prod" ? 20 : 5

    container {
      name   = "pixelmentor-api"
      image  = "${var.acr_login_server}/pixelmentor-api:${var.container_image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "ENVIRONMENT"
        value = var.environment
      }
      env {
        name  = "AZURE_OPENAI_ENDPOINT"
        value = var.openai_endpoint
      }
      env {
        name  = "AZURE_SEARCH_ENDPOINT"
        value = var.search_endpoint
      }
      env {
        name  = "COSMOS_ENDPOINT"
        value = var.cosmos_endpoint
      }
      env {
        name  = "STORAGE_ACCOUNT_NAME"
        value = var.storage_account_name
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = var.app_insights_connection_string
      }
      env {
        name        = "AZURE_OPENAI_API_KEY"
        secret_name = "openai-api-key"
      }
      env {
        name        = "COSMOS_CONNECTION_STRING"
        secret_name = "cosmos-connection-string"
      }
      env {
        name        = "STORAGE_CONNECTION_STRING"
        secret_name = "storage-connection-string"
      }
      env {
        name        = "AZURE_SEARCH_KEY"
        secret_name = "search-admin-key"
      }
	  env {
		name        = "ENTRA_TENANT_ID"
		secret_name = "entra-tenant-id"
	  }
	  env {
		name        = "ENTRA_API_CLIENT_ID"
		secret_name = "entra-api-client-id"
	  }
	  env {
		name        = "ENTRA_ANDROID_CLIENT_ID"
		secret_name = "entra-android-client-id"
	  }

      liveness_probe {
        transport               = "HTTP"
        path                    = "/health"
        port                    = 8000
        initial_delay           = 10
        failure_count_threshold = 3
      }

      readiness_probe {
        transport               = "HTTP"
        path                    = "/health/ready"
        port                    = 8000
        success_count_threshold = 1
      }
    }

    http_scale_rule {
      name                = "http-scale"
      concurrent_requests = "50"
    }
  }

  ingress {
    external_enabled = true
    target_port      = 8000
    transport = "auto"

    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}

output "api_fqdn"       { value = azurerm_container_app.api.latest_revision_fqdn }
output "api_url"        { value = "https://${azurerm_container_app.api.latest_revision_fqdn}" }
output "environment_id" { value = azurerm_container_app_environment.main.id }
