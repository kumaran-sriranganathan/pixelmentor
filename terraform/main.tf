###############################################################################
# PixelMentor – Root Terraform Configuration
# Azure Best Practices:
#   - Remote state in Azure Blob Storage with state locking
#   - Modular design (one module per resource group of concerns)
#   - All secrets via Azure Key Vault (never in state/vars)
#   - Private endpoints for PaaS services
#   - Managed Identities (no service principal secrets)
###############################################################################

terraform {
  required_version = ">= 1.7.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.110"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 2.53"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    time = {
      source  = "hashicorp/time"
      version = "~> 0.11"
    }
  }

  # Remote state — Azure Blob Storage
  # Fill in after running scripts/bootstrap.sh
  backend "azurerm" {
    resource_group_name  = "pixelmentor-tfstate-rg"
    storage_account_name = "pmtfstate26822a"          # set by bootstrap
    container_name       = "tfstate"
    key                  = "pixelmentor.terraform.tfstate"
    use_oidc             = true                 # Federated auth — no secrets
  }
}

provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_destroy    = false   # Protect secrets in prod
      recover_soft_deleted_key_vaults = true
    }
    resource_group {
      prevent_deletion_if_contains_resources = true
    }
    cognitive_account {
      purge_soft_delete_on_destroy = false
    }
  }
  use_oidc = true
}

provider "azuread" {
  use_oidc = true
}

###############################################################################
# Random suffix — ensures globally unique resource names
###############################################################################
resource "random_string" "suffix" {
  length  = 5
  special = false
  upper   = false
}

locals {
  # suffix_override in tfvars lets you force new names if old ones are stuck in soft-delete
  suffix   = var.suffix_override != "" ? var.suffix_override : random_string.suffix.result
  env      = var.environment
  location = var.location
  tags = {
    Project     = "PixelMentor"
    Environment = var.environment
    ManagedBy   = "Terraform"
    CostCenter  = "engineering"
  }
}

###############################################################################
# Modules
###############################################################################

module "resource_group" {
  source      = "./modules/resource_group"
  suffix      = local.suffix
  environment = local.env
  location    = local.location
  tags        = local.tags
}

module "networking" {
  source              = "./modules/networking"
  suffix              = local.suffix
  environment         = local.env
  location            = local.location
  resource_group_name = module.resource_group.name
  tags                = local.tags
}

module "security" {
  source              = "./modules/security"
  suffix              = local.suffix
  environment         = local.env
  location            = local.location
  resource_group_name = module.resource_group.name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  subnet_id           = module.networking.private_endpoint_subnet_id
  tags                = local.tags
}

module "openai" {
  source              = "./modules/openai"
  suffix              = local.suffix
  environment         = local.env
  location            = local.location
  resource_group_name = module.resource_group.name
  key_vault_id        = module.security.key_vault_id
  subnet_id           = module.networking.private_endpoint_subnet_id
  private_dns_zone_id = module.networking.openai_private_dns_zone_id
  tags                = local.tags

  depends_on = [module.security]   # Waits for RBAC to propagate
}

module "storage" {
  source              = "./modules/storage"
  suffix              = local.suffix
  environment         = local.env
  location            = local.location
  resource_group_name = module.resource_group.name
  key_vault_id        = module.security.key_vault_id
  subnet_id           = module.networking.private_endpoint_subnet_id
  private_dns_zone_id = module.networking.blob_private_dns_zone_id
  tags                = local.tags

  depends_on = [module.security]   # Waits for RBAC to propagate
}

module "cosmos" {
  source              = "./modules/cosmos"
  suffix              = local.suffix
  environment         = local.env
  location            = local.location
  resource_group_name = module.resource_group.name
  key_vault_id        = module.security.key_vault_id
  subnet_id           = module.networking.private_endpoint_subnet_id
  private_dns_zone_id = module.networking.cosmos_private_dns_zone_id
  tags                = local.tags

  depends_on = [module.security]   # Waits for RBAC to propagate
}

module "search" {
  source              = "./modules/search"
  suffix              = local.suffix
  environment         = local.env
  location            = local.location
  resource_group_name = module.resource_group.name
  key_vault_id        = module.security.key_vault_id
  subnet_id           = module.networking.private_endpoint_subnet_id
  private_dns_zone_id = module.networking.search_private_dns_zone_id
  sku                 = var.search_sku
  tags                = local.tags

  depends_on = [module.security]   # Waits for RBAC to propagate
}

module "monitoring" {
  source              = "./modules/monitoring"
  suffix              = local.suffix
  environment         = local.env
  location            = local.location
  resource_group_name = module.resource_group.name
  tags                = local.tags
}

module "container_apps" {
  source                         = "./modules/container_apps"
  suffix                         = local.suffix
  environment                    = local.env
  location                       = local.location
  resource_group_name            = module.resource_group.name
  log_analytics_workspace_id     = module.monitoring.log_analytics_workspace_id
  app_insights_connection_string = module.monitoring.app_insights_connection_string
  acr_login_server               = module.security.acr_login_server
  acr_id                         = module.security.acr_id
  cosmos_endpoint                = module.cosmos.endpoint
  cosmos_connection_string       = module.cosmos.primary_connection_string
  storage_account_name           = module.storage.account_name
  storage_connection_string      = module.storage.primary_connection_string
  openai_endpoint                = module.openai.endpoint
  openai_api_key                 = module.openai.primary_key
  search_endpoint                = module.search.endpoint
  search_key                     = module.search.primary_key
  identity_id                    = module.security.identity_id
  identity_client_id             = module.security.identity_client_id
  tags                           = local.tags

  depends_on = [module.security, module.cosmos, module.storage, module.openai, module.search]
}

###############################################################################
# Current subscription / tenant info
###############################################################################
data "azurerm_client_config" "current" {}
