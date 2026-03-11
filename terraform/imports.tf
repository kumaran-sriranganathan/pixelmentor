###############################################################################
# imports.tf — Existing Azure Resources
#
# These import blocks tell Terraform "this resource already exists in Azure,
# adopt it instead of trying to create it".
#
# Safe to run even if resources are already in state — Terraform skips them.
###############################################################################

# ── Key Vault Role Assignments ────────────────────────────────────────────────
import {
  to = module.security.azurerm_role_assignment.terraform_keyvault_admin
  id = "/subscriptions/76798183-b853-41c3-80bd-d143b56985c3/resourceGroups/pixelmentor-dev-rg/providers/Microsoft.KeyVault/vaults/pm-dev-kv-dev01/providers/Microsoft.Authorization/roleAssignments/31faad1d-c370-45bc-81ec-a94278b29f5c"
}

import {
  to = module.security.azurerm_role_assignment.app_keyvault_secrets
  id = "/subscriptions/76798183-b853-41c3-80bd-d143b56985c3/resourceGroups/pixelmentor-dev-rg/providers/Microsoft.KeyVault/vaults/pm-dev-kv-dev01/providers/Microsoft.Authorization/roleAssignments/ad2a34f1-5bce-a0e3-8889-3c4f32d91770"
}

# NOTE: Container App (pm-dev-api) is NOT imported here because it is in a
# failed provisioning state and needs to be deleted and recreated by Terraform.
