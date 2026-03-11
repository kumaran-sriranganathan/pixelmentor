# terraform/environments/dev/dev.tfvars
# Use: terraform apply -var-file="environments/dev/dev.tfvars"

environment         = "dev"
location            = "australiaeast"
gpt4o_capacity      = 20
container_image_tag = "latest"
alert_email         = "your-email@domain.com"

# Suffix override — forces new resource names
# Old suffix "24zpw" is stuck in soft-delete, so we use a new one
# Change this value any time you hit soft-delete conflicts
suffix_override     = "dev01"
