#!/usr/bin/env bash
# =============================================================================
# PixelMentor — GitHub Actions OIDC Setup Script
# =============================================================================
# Run this once to wire up GitHub → Azure trust for both dev and prod.
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

echo "🔧 Setting subscription context..."
az account set --subscription "$SUBSCRIPTION_ID"

# =============================================================================
# DEV — App Registration + Federated Credentials
# =============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Setting up DEV service principal"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Create app registration
DEV_APP_ID=$(az ad app create \
  --display-name "$DEV_APP_NAME" \
  --query appId -o tsv)
echo "✅ Dev App ID: $DEV_APP_ID"

# Create service principal
DEV_SP_OBJ_ID=$(az ad sp create --id "$DEV_APP_ID" --query id -o tsv)
echo "✅ Dev SP Object ID: $DEV_SP_OBJ_ID"

# Federated credential: dev branch pushes
az ad app federated-credential create \
  --id "$DEV_APP_ID" \
  --parameters "{
    \"name\": \"github-dev-branch\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/dev\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"
echo "✅ Dev federated credential (branch: dev)"

# Federated credential: pull requests targeting dev or main
az ad app federated-credential create \
  --id "$DEV_APP_ID" \
  --parameters "{
    \"name\": \"github-pull-requests\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:pull_request\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"
echo "✅ Dev federated credential (pull_request)"

# Role assignments — dev resource group only
az role assignment create \
  --assignee-object-id "$DEV_SP_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Contributor" \
  --scope "/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${DEV_RG}"
echo "✅ Dev Contributor role on $DEV_RG"

# Storage Blob Data Contributor for Terraform state
az role assignment create \
  --assignee-object-id "$DEV_SP_OBJ_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "Storage Blob Data Contributor" \
  --scope "/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${DEV_RG}"
echo "✅ Dev Storage Blob Data Contributor on $DEV_RG"

# =============================================================================
# PROD — App Registration + Federated Credentials (more restricted)
# =============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Setting up PROD service principal"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PROD_APP_ID=$(az ad app create \
  --display-name "$PROD_APP_NAME" \
  --query appId -o tsv)
echo "✅ Prod App ID: $PROD_APP_ID"

PROD_SP_OBJ_ID=$(az ad sp create --id "$PROD_APP_ID" --query id -o tsv)
echo "✅ Prod SP Object ID: $PROD_SP_OBJ_ID"

# Federated credential: main branch only (prod is main-only)
az ad app federated-credential create \
  --id "$PROD_APP_ID" \
  --parameters "{
    \"name\": \"github-main-branch\",
    \"issuer\": \"https://token.actions.githubusercontent.com\",
    \"subject\": \"repo:${GITHUB_ORG}/${GITHUB_REPO}:ref:refs/heads/main\",
    \"audiences\": [\"api://AzureADTokenExchange\"]
  }"
echo "✅ Prod federated credential (branch: main)"

# NOTE: Prod RG doesn't exist yet — run these after prod infra is deployed:
# az role assignment create \
#   --assignee-object-id "$PROD_SP_OBJ_ID" \
#   --assignee-principal-type ServicePrincipal \
#   --role "Contributor" \
#   --scope "/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${PROD_RG}"
#
# az role assignment create \
#   --assignee-object-id "$PROD_SP_OBJ_ID" \
#   --assignee-principal-type ServicePrincipal \
#   --role "Storage Blob Data Contributor" \
#   --scope "/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${PROD_RG}"

echo ""
echo "⚠️  Prod role assignments are commented out above."
echo "    Run them after prod infrastructure is deployed."

# =============================================================================
# Output — copy these into GitHub Actions variables/secrets
# =============================================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  GitHub Actions Variables to set"
echo "  (repo Settings → Secrets and variables → Actions → Variables)"
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
