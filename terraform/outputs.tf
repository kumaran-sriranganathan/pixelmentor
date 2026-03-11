###############################################################################
# PixelMentor – Outputs
###############################################################################

output "resource_group_name" {
  description = "Main resource group name"
  value       = module.resource_group.name
}

output "container_app_fqdn" {
  description = "Public FQDN of the API Container App"
  value       = module.container_apps.api_fqdn
}

output "acr_login_server" {
  description = "Azure Container Registry login server"
  value       = module.security.acr_login_server
}

output "key_vault_uri" {
  description = "Key Vault URI for secrets management"
  value       = module.security.key_vault_uri
  sensitive   = true
}

output "openai_endpoint" {
  description = "Azure OpenAI endpoint"
  value       = module.openai.endpoint
  sensitive   = true
}

output "cosmos_endpoint" {
  description = "Cosmos DB endpoint"
  value       = module.cosmos.endpoint
  sensitive   = true
}

output "storage_account_name" {
  description = "Storage account for photos"
  value       = module.storage.account_name
}

output "app_insights_instrumentation_key" {
  description = "Application Insights key"
  value       = module.monitoring.instrumentation_key
  sensitive   = true
}
