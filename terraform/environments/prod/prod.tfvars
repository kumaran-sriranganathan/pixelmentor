# terraform/environments/prod/prod.tfvars
# Use: terraform apply -var-file="environments/prod/prod.tfvars"

environment         = "prod"
location            = "australiaeast"
gpt4o_capacity      = 80
container_image_tag = "stable"
alert_email         = "ops-team@domain.com"
