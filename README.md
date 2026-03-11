# PixelMentor — Azure Backend & Infrastructure

AI-powered photography education backend.
Built with FastAPI + LangGraph on Azure Container Apps,
infrastructure managed by Terraform.

---

## Architecture Overview

***REMOVED***
Android App
    │
    ▼
Azure Container Apps (FastAPI)
    │
    ├── Azure OpenAI (GPT-4o + DALL-E 3 + Embeddings)
    ├── Azure AI Vision (photo metadata)
    ├── Azure AI Search (RAG lesson recommendations)
    ├── Azure Cosmos DB (user data, history)
    └── Azure Blob Storage (photo uploads)

All secrets → Azure Key Vault
All auth    → Managed Identity (no passwords)
All traffic → Private Endpoints (no public PaaS exposure)
***REMOVED***

---

## Prerequisites — Install These First

| Tool | Version | Install |
|---|---|---|
| Azure CLI | 2.65+ | https://learn.microsoft.com/cli/azure/install-azure-cli |
| Terraform | 1.7+ | https://developer.hashicorp.com/terraform/install |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| Python | 3.12+ | https://www.python.org/downloads |

---

## Step 1 — Azure Login

***REMOVED***bash
# Login to Azure
az login

# List your subscriptions
az account list --output table

# Set the subscription you want to use
az account set --subscription "YOUR-SUBSCRIPTION-ID"

# Verify you are using the right subscription
az account show
***REMOVED***

---

## Step 2 — Bootstrap Remote Terraform State

This creates the Azure Storage Account that holds your Terraform state file.
Run this ONCE only.

***REMOVED***bash
# Make the script executable
chmod +x scripts/bootstrap.sh

# Run it
./scripts/bootstrap.sh
***REMOVED***

The script will print the storage account name it created.
Copy it and update `terraform/main.tf` backend block:

***REMOVED***hcl
backend "azurerm" {
  resource_group_name  = "pixelmentor-tfstate-rg"
  storage_account_name = "pmtfstateXXXXXX"    # ← paste the printed name here
  container_name       = "tfstate"
  key                  = "pixelmentor.terraform.tfstate"
  use_oidc             = true
}
***REMOVED***

---

## Step 3 — Initialise Terraform

***REMOVED***bash
cd terraform

# Download providers and configure remote state
terraform init

# You should see:
# "Terraform has been successfully initialized!"
***REMOVED***

---

## Step 4 — Plan the Infrastructure (Preview)

***REMOVED***bash
# See exactly what Terraform will create — no changes yet
terraform plan -var-file="environments/dev/dev.tfvars"
***REMOVED***

Read through the plan. You should see ~40 resources being created.
This is safe — nothing is created yet.

---

## Step 5 — Apply (Create Infrastructure)

***REMOVED***bash
# Create all Azure resources
terraform apply -var-file="environments/dev/dev.tfvars"

# Terraform will ask: "Do you want to perform these actions?"
# Type:  yes
# Then press Enter

# Takes approximately 10-15 minutes for the first run
***REMOVED***

When complete you will see outputs like:
***REMOVED***
container_app_fqdn  = "pm-dev-api.graystone-12345.australiaeast.azurecontainerapps.io"
acr_login_server    = "pmdevacr12345.azurecr.io"
***REMOVED***

---

## Step 6 — Set Up Local Backend Development

***REMOVED***bash
cd backend

# Create a virtual environment
python -m venv venv

# Activate it
# On Mac/Linux:
source venv/bin/activate
# On Windows:
venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Copy the environment template
cp .env.template .env

# Edit .env and fill in your Azure resource values
# (get values from Azure Portal or from Terraform outputs)
nano .env      # or open in VS Code: code .env
***REMOVED***

---

## Step 7 — Run the Backend Locally

***REMOVED***bash
# Make sure your virtual environment is activated
# Make sure .env is filled in

# Start the server
uvicorn app.main:app --reload --port 8000

# You should see:
# INFO: Uvicorn running on http://127.0.0.1:8000

# Open the API docs in your browser:
# http://localhost:8000/docs
***REMOVED***

---

## Step 8 — Build & Deploy to Azure

***REMOVED***bash
# Go back to the project root
cd ..

# Make deploy script executable
chmod +x scripts/deploy.sh

# Deploy to dev environment with tag "latest"
./scripts/deploy.sh dev latest

# The script will:
# 1. Read ACR details from Terraform outputs
# 2. Build the Docker image
# 3. Push it to Azure Container Registry
# 4. Update the Container App with the new image
# 5. Run a health check to confirm it's live
***REMOVED***

---

## Step 9 — Test the Deployed API

***REMOVED***bash
# Get the API URL
cd terraform
API_URL=$(terraform output -raw container_app_fqdn)
cd ..

# Health check
curl https://$API_URL/health

# Expected response:
# {"status":"alive","environment":"dev"}
***REMOVED***

---

## Deploying to Production

***REMOVED***bash
cd terraform

# Plan prod first — always review changes
terraform plan -var-file="environments/prod/prod.tfvars"

# Apply to prod
terraform apply -var-file="environments/prod/prod.tfvars"

# Deploy the app to prod
cd ..
./scripts/deploy.sh prod v1.0.0
***REMOVED***

---

## Destroying Resources (Save Cost)

***REMOVED***bash
cd terraform

# Destroy dev environment when not in use
terraform destroy -var-file="environments/dev/dev.tfvars"

# Type: yes to confirm
***REMOVED***

---

## Project Structure

***REMOVED***
pixelmentor/
├── terraform/
│   ├── main.tf                    # Root configuration
│   ├── variables.tf               # Input variables
│   ├── outputs.tf                 # Outputs (URLs, names)
│   ├── environments/
│   │   ├── dev/dev.tfvars         # Dev values
│   │   └── prod/prod.tfvars       # Prod values
│   └── modules/
│       ├── resource_group/        # Resource group
│       ├── networking/            # VNet, subnets, DNS
│       ├── security/              # Key Vault, ACR, Identity
│       ├── openai/                # Azure OpenAI + models
│       ├── cosmos/                # Cosmos DB + collections
│       ├── storage/               # Blob Storage + CDN
│       ├── search/                # Azure AI Search
│       ├── monitoring/            # Log Analytics + App Insights
│       └── container_apps/        # Container Apps environment + app
├── backend/
│   ├── app/
│   │   ├── main.py                # FastAPI app entry point
│   │   ├── config.py              # Settings (from env vars)
│   │   ├── agents/
│   │   │   └── photo_coach.py     # LangGraph PhotoCoach agent
│   │   ├── routers/
│   │   │   ├── analyze.py         # POST /analyze/photo
│   │   │   ├── tutor.py           # POST /tutor/chat (streaming)
│   │   │   ├── lessons.py         # GET /lessons
│   │   │   ├── users.py           # GET/PUT /users
│   │   │   └── health.py          # GET /health
│   │   ├── models/
│   │   │   └── analysis.py        # Pydantic schemas
│   │   └── middleware/
│   │       ├── auth.py            # Azure AD JWT validation
│   │       └── rate_limiter.py    # Per-IP rate limiting
│   ├── Dockerfile                 # Multi-stage production image
│   ├── requirements.txt           # Python dependencies
│   └── .env.template              # Local dev env template
└── scripts/
    ├── bootstrap.sh               # One-time tfstate setup
    └── deploy.sh                  # Build + push + deploy
***REMOVED***

---

## Security Checklist

- No secrets in code or Terraform state
- All secrets stored in Azure Key Vault
- Container Apps reads secrets from Key Vault via Managed Identity
- All PaaS services (OpenAI, Cosmos, Storage, Search) on private endpoints
- No public network access on any data service
- Non-root Docker container
- TLS 1.2 minimum everywhere
- Rate limiting on all AI endpoints
- Azure AD token validation on all API routes

---

## Estimated Monthly Cost (Dev, 1000 active users)

| Service | Est. Cost |
|---|---|
| Container Apps (0.5 CPU) | ~$40 |
| Azure OpenAI (GPT-4o) | ~$150–300 |
| Cosmos DB (serverless) | ~$20 |
| Blob Storage | ~$15 |
| AI Search (Basic) | ~$75 |
| Monitoring | ~$10 |
| **Total** | **~$310–$460/month** |
