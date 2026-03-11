#!/bin/bash
###############################################################################
# scripts/bootstrap.sh
# Run ONCE before terraform init to create the remote state storage account.
# This is the only resource NOT managed by Terraform (it manages Terraform).
#
# Prerequisites:
#   - Azure CLI installed: https://learn.microsoft.com/en-us/cli/azure/install-azure-cli
#   - Logged in: az login
#   - Correct subscription selected: az account set --subscription "YOUR_SUB_ID"
#
# Usage:
#   chmod +x scripts/bootstrap.sh
#   ./scripts/bootstrap.sh
###############################################################################

set -euo pipefail

# ── Configuration — edit these ────────────────────────────────────────────────
RESOURCE_GROUP="pixelmentor-tfstate-rg"
LOCATION="australiaeast"                # Match your terraform location variable
STORAGE_ACCOUNT="pmtfstate$(openssl rand -hex 3)"   # Globally unique name
CONTAINER_NAME="tfstate"

echo "=================================================="
echo " PixelMentor — Terraform Bootstrap"
echo "=================================================="
echo ""

# ── Step 1: Create resource group ─────────────────────────────────────────────
echo "1. Creating resource group: $RESOURCE_GROUP"
az group create \
  --name "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --output none
echo "   ✓ Resource group created"

# ── Step 2: Create storage account ────────────────────────────────────────────
echo "2. Creating storage account: $STORAGE_ACCOUNT"
az storage account create \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --sku Standard_GRS \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --allow-blob-public-access false \
  --output none
echo "   ✓ Storage account created"

# ── Step 3: Enable versioning (protects tfstate from accidental deletion) ─────
echo "3. Enabling blob versioning on tfstate storage"
az storage account blob-service-properties update \
  --account-name "$STORAGE_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --enable-versioning true \
  --output none
echo "   ✓ Versioning enabled"

# ── Step 4: Create container ───────────────────────────────────────────────────
echo "4. Creating blob container: $CONTAINER_NAME"
az storage container create \
  --name "$CONTAINER_NAME" \
  --account-name "$STORAGE_ACCOUNT" \
  --auth-mode login \
  --output none
echo "   ✓ Container created"

# ── Step 5: Print next steps ───────────────────────────────────────────────────
echo ""
echo "=================================================="
echo " Bootstrap complete!"
echo "=================================================="
echo ""
echo "Now update terraform/main.tf backend block with:"
echo ""
echo "  backend \"azurerm\" {"
echo "    resource_group_name  = \"$RESOURCE_GROUP\""
echo "    storage_account_name = \"$STORAGE_ACCOUNT\""
echo "    container_name       = \"$CONTAINER_NAME\""
echo "    key                  = \"pixelmentor.terraform.tfstate\""
echo "    use_oidc             = true"
echo "  }"
echo ""
echo "Then run:"
echo "  cd terraform"
echo "  terraform init"
echo ""
