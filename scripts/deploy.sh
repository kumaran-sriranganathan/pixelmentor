#!/bin/bash
###############################################################################
# scripts/deploy.sh
# Builds the Docker image and pushes to Azure Container Registry,
# then triggers a new Container Apps revision.
#
# Usage:
#   ./scripts/deploy.sh dev latest
#   ./scripts/deploy.sh prod v1.2.3
###############################################################################

set -euo pipefail

ENVIRONMENT=${1:-dev}
IMAGE_TAG=${2:-latest}

# Read ACR login server from Terraform output
echo "Reading Terraform outputs..."
cd terraform
ACR_LOGIN_SERVER=$(terraform output -raw acr_login_server)
cd ..

IMAGE_NAME="$ACR_LOGIN_SERVER/pixelmentor-api:$IMAGE_TAG"

echo "=================================================="
echo " PixelMentor — Build & Deploy"
echo " Environment : $ENVIRONMENT"
echo " Image Tag   : $IMAGE_TAG"
echo " ACR         : $ACR_LOGIN_SERVER"
echo "=================================================="

# ── Step 1: Login to ACR ──────────────────────────────────────────────────────
echo ""
echo "1. Logging into ACR..."
az acr login --name "${ACR_LOGIN_SERVER%%.*}"

# ── Step 2: Build Docker image ────────────────────────────────────────────────
echo "2. Building Docker image..."
docker build \
  --platform linux/amd64 \
  --tag "$IMAGE_NAME" \
  --tag "$ACR_LOGIN_SERVER/pixelmentor-api:latest" \
  ./backend

echo "   ✓ Image built: $IMAGE_NAME"

# ── Step 3: Push to ACR ───────────────────────────────────────────────────────
echo "3. Pushing image to ACR..."
docker push "$IMAGE_NAME"
docker push "$ACR_LOGIN_SERVER/pixelmentor-api:latest"
echo "   ✓ Image pushed"

# ── Step 4: Update Container App with new image ───────────────────────────────
echo "4. Deploying new revision to Container Apps..."
RESOURCE_GROUP="pixelmentor-$ENVIRONMENT-rg"
CONTAINER_APP="pm-$ENVIRONMENT-api"

az containerapp update \
  --name "$CONTAINER_APP" \
  --resource-group "$RESOURCE_GROUP" \
  --image "$IMAGE_NAME" \
  --output none

echo "   ✓ Container App updated"

# ── Step 5: Check deployment health ──────────────────────────────────────────
echo "5. Waiting for health check..."
sleep 15

API_URL=$(cd terraform && terraform output -raw container_app_fqdn)
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "https://$API_URL/health")

if [ "$HTTP_STATUS" == "200" ]; then
  echo "   ✓ Health check passed (HTTP $HTTP_STATUS)"
else
  echo "   ✗ Health check failed (HTTP $HTTP_STATUS)"
  echo "   Check logs: az containerapp logs show --name $CONTAINER_APP --resource-group $RESOURCE_GROUP"
  exit 1
fi

echo ""
echo "=================================================="
echo " Deployment complete!"
echo " API URL: https://$API_URL"
echo "=================================================="
