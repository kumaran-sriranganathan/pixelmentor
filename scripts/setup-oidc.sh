#!/usr/bin/env bash
# =============================================================================
# PixelMentor — GitHub Actions OIDC Setup Script
# =============================================================================
# Run this once to wire up GitHub → Azure trust for both dev and prod.
# Safe to re-run — idempotent (reuses existing app registrations/SPs).
#
# Prerequisites:
#   - Azure CLI installed and logged in (az login)
#   - Owner or User Access Administrator on the subscription
#   - GitHub repo created at: github.com/project-ksnz/pixelmentor
# =============================================================================

set -euo pipefail

# ── Config ───────────────────────────────────────────────────────────────────
TENANT_ID="6221e7e5-a175-451d-9eb7-e7b9109786ca"                        # az account show --query tenantId -o tsv
SUBSCRIPTION_ID="76798183-b853-41c3-80bd-d143b56985c3"
GITHUB_ORG="project-ksnz"
GITHUB_REPO="pixelmentor"

DEV_APP_NAME="pixelmentor-github-actions-dev"
PROD_APP_NAME="pixelmentor-github-actions-prod"

DEV_RG="pixelmentor-dev-rg"
PROD_RG="pixelmentor-prod-rg"                     # Create when deploying prod infra
# ─────────────────────────────────────────────────────────────────────────────

# Helper: idempotent app registration
get_or_create_app() {
  local display_name="$1"
  local existing
  existing=$(az ad app list --display-name "$display_name" --query "[0].appId" -o tsv 2>/dev/null || true)
  if [[ -n "$existing" && "$existing" != "None" ]]; then
    echo "$existing"
  else
    az ad app create --display-name "$display_name" --query appId -o tsv
  fi
}

# Helper: idempotent service principal
get_or_create_sp() {
  local app_id="$1"
  local existing
  existing=$(az ad sp list --filter "appId eq '${app_id}'" --query "[0].id" -o tsv 2>/dev/null || true)
  if [[ -n "$existing" && "$existing" != "None" ]]; then
    echo "$existing"
  else
    az ad sp create --id "$app_id" --query id -o tsv
  fi
}

# Helper: idempotent federated credential
create_federated_credential() {
  local app_id="$1"
  local name="$2"
  local subject="$3"

  local existing
  existing=$(az ad app federated-credential list --id "$app_id" --query "[?name=='${name}'].name" -o tsv 2>/dev/null || true)
  if [[ -n "$existing" ]]; then
    echo "ℹ️  Federated credential '${name}' already exists, skipping"
  else
    az ad app federated-credential create \
      --id "$app_id" \
      --parameters "{
        \"name\": \"${name}\",
        \"issuer\": \"https://token.actions.githubusercontent.com\",
        \"subject\": \"${subject}\",
        \"audiences\": [\"api://AzureADTokenExchange\"]
      }" > /dev/null
    echo "✅ Federated credential: ${name}"
  fi
}

# Helper: idempotent role assignment
# Uses --subscription flag directly on every az role command to avoid
# context being dropped by az ad commands
assign_role() {
  local sp_obj_id="$1"
  local role="$2"
  local scope="$3"

  local existing
  existing=$(az role assignment list \
    --subscription "$SUBSCRIPTION_ID" \
    --assignee "$sp_obj_id" \
    --role "$role" \
    --scope "$scope" \
    --query "[0].id" -o tsv 2>/dev/null || true)

  if [[ -n "$existing" && "$existing" != "None" ]]; then
    echo "ℹ️  Role '${role}' already assigned, skipping"
  else
    az role assignment create \
      --subscription "$SUBSCRIPTION_ID" \
      --assignee-object-id "$sp_obj_id" \
      --assignee-principal-type ServicePrincipal \
      --role "$role" \
      --scope "$scope" > /dev/null
    echo "✅ Assigned '${role}' on: ${scope##*/}"
  fi
}

# =============================================================================
# Start
# =============================================================================
echo "🔧 Setting subscription context..."
az account set --subscription "$SUBSCRIPTION_ID"

# =============================================================================
# DEV — App Registration + Federated Credentials
# =============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Setting up DEV service principal"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

DEV_APP_ID=$(get_or_create_app "$DEV_APP_NAME")
echo "✅ Dev App ID: $DEV_APP_ID"

DEV_SP_OBJ_ID=$(get_or_create_sp "$DEV_APP_ID")
echo "✅ Dev SP Object ID: $DEV_SP_OBJ_ID"

create_federated_credential \
  "$DEV_APP_ID" \
  "github-dev-branch" \
  "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/dev"

create_federated_credential \
  "$DEV_APP_ID" \
  "github-pull-requests" \
  "repo:${GITHUB_ORG}/${GITHUB_REPO}:pull_request"

SUB_SCOPE="/subscriptions/${SUBSCRIPTION_ID}"
DEV_RG_SCOPE="${SUB_SCOPE}/resourceGroups/${DEV_RG}"

assign_role "$DEV_SP_OBJ_ID" "Contributor"                   "$DEV_RG_SCOPE"
assign_role "$DEV_SP_OBJ_ID" "Storage Blob Data Contributor"  "$DEV_RG_SCOPE"

# =============================================================================
# PROD — App Registration + Federated Credentials
# =============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Setting up PROD service principal"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PROD_APP_ID=$(get_or_create_app "$PROD_APP_NAME")
echo "✅ Prod App ID: $PROD_APP_ID"

PROD_SP_OBJ_ID=$(get_or_create_sp "$PROD_APP_ID")
echo "✅ Prod SP Object ID: $PROD_SP_OBJ_ID"

create_federated_credential \
  "$PROD_APP_ID" \
  "github-main-branch" \
  "repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main"

echo ""
echo "⚠️  Prod role assignments skipped — prod RG doesn't exist yet."
echo "    After deploying prod infrastructure, uncomment the two lines"
echo "    at the bottom of this script and re-run it."

# Uncomment these once prod infra is deployed:
# PROD_RG_SCOPE="${SUB_SCOPE}/resourceGroups/${PROD_RG}"
# assign_role "$PROD_SP_OBJ_ID" "Contributor"                   "$PROD_RG_SCOPE"
# assign_role "$PROD_SP_OBJ_ID" "Storage Blob Data Contributor"  "$PROD_RG_SCOPE"

# =============================================================================
# Output — copy these into GitHub Actions variables
# =============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  GitHub Actions Variables to set"
echo "  (repo Settings → Secrets and variables → Actions → Variables tab)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  AZURE_TENANT_ID        = $TENANT_ID"
echo "  AZURE_SUBSCRIPTION_ID  = $SUBSCRIPTION_ID"
echo "  AZURE_CLIENT_ID        = $DEV_APP_ID          ← used by dev + PR workflows"
echo "  AZURE_CLIENT_ID_PROD   = $PROD_APP_ID         ← used by prod workflow"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  GitHub Environments to create"
echo "  (repo Settings → Environments)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  1. dev        — no approval required"
echo "  2. prod-plan  — no approval required (just shows the plan)"
echo "  3. prod       — add required reviewers here (this is the approval gate)"
echo ""
echo "✅ OIDC setup complete!"
