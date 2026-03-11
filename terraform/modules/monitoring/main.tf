###############################################################################
# Module: monitoring
# Creates: Log Analytics Workspace, Application Insights, Alert Rules
###############################################################################

variable "suffix"              {}
variable "environment"         {}
variable "location"            {}
variable "resource_group_name" {}
variable "tags"                {}
variable "alert_email"         { default = "" }

resource "azurerm_log_analytics_workspace" "main" {
  name                = "pm-${var.environment}-law-${var.suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  sku                 = "PerGB2018"
  retention_in_days   = var.environment == "prod" ? 90 : 30
  tags                = var.tags
}

resource "azurerm_application_insights" "main" {
  name                = "pm-${var.environment}-appinsights-${var.suffix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "web"
  tags                = var.tags
}

# ── Alert: High error rate ────────────────────────────────────────────────────
resource "azurerm_monitor_action_group" "main" {
  name                = "pm-${var.environment}-alerts"
  resource_group_name = var.resource_group_name
  short_name          = "pm-alerts"
  tags                = var.tags

  dynamic "email_receiver" {
    for_each = var.alert_email != "" ? [1] : []
    content {
      name          = "ops-email"
      email_address = var.alert_email
    }
  }
}

resource "azurerm_monitor_metric_alert" "high_error_rate" {
  name                = "pm-${var.environment}-high-error-rate"
  resource_group_name = var.resource_group_name
  scopes              = [azurerm_application_insights.main.id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT15M"
  tags                = var.tags

  criteria {
    metric_namespace = "microsoft.insights/components"
    metric_name      = "requests/failed"
    aggregation      = "Count"
    operator         = "GreaterThan"
    threshold        = 10
  }

  action {
    action_group_id = azurerm_monitor_action_group.main.id
  }
}

output "log_analytics_workspace_id"     { value = azurerm_log_analytics_workspace.main.id }
output "app_insights_connection_string" { value = azurerm_application_insights.main.connection_string }
output "instrumentation_key"            { value = azurerm_application_insights.main.instrumentation_key }
