#!/bin/bash
set -e

echo "Fetching secrets from Vault..."

# CADP Secrets
export CADP_KEY_MANAGER_HOST=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=host secret/cadp)
export CADP_KEY_MANAGER_PORT=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=port secret/cadp)
export CADP_REGISTRATION_TOKEN=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=token secret/cadp)
export CADP_PROTECTION_POLICY_NAME=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=policy secret/cadp)
export CADP_USER_NAME=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=user secret/cadp)

# CRDP Secrets
export CRDP_ENDPOINT=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=endpoint secret/crdp)
export CRDP_TLS=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=tls secret/crdp)
export CRDP_POLICY=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=policy secret/crdp)
export CRDP_USER_NAME=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=user secret/crdp)
export CRDP_JWT=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=jwt secret/crdp)

echo "Secrets exported to environment."

# Start Tomcat
echo "Starting Tomcat..."
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$BASE_DIR/tomcat_mysql_docker"

docker-compose up -d tomcat

echo "Tomcat started with Vault secrets."
