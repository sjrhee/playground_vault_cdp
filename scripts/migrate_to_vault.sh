#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER_DIR="$BASE_DIR/tomcat_mysql_docker"
PROP_DIR="$BASE_DIR/src/main/resources"

echo "Step 1: Start Vault Container"
cd "$DOCKER_DIR"
docker-compose up -d vault

echo "Waiting for Vault to be ready..."
until docker exec demo_vault vault status > /dev/null 2>&1; do
    echo "Vault is not ready yet..."
    sleep 1
done
echo "Vault is ready."

# Enable KV v2 at secret/ if not already (Dev mode enables it at secret/ by default usually, but let's be sure or just use secret/)
# In dev mode, 'secret' mount is usually kv v2.

echo "Step 2: Parse Properties and Migrate to Vault"

get_prop() {
    grep "^$1=" "$2" | cut -d'=' -f2
}

# CADP
CADP_PROP="$PROP_DIR/cadp.properties"
if [ -f "$CADP_PROP" ]; then
    echo "found cadp.properties, migrating..."
    CADP_HOST=$(get_prop "keyManagerHost" "$CADP_PROP")
    CADP_PORT=$(get_prop "keyManagerPort" "$CADP_PROP")
    CADP_TOKEN=$(get_prop "registrationToken" "$CADP_PROP")
    CADP_POLICY=$(get_prop "protectionPolicyName" "$CADP_PROP")
    CADP_USER=$(get_prop "userName" "$CADP_PROP")

    docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv put secret/cadp \
        host="$CADP_HOST" \
        port="$CADP_PORT" \
        token="$CADP_TOKEN" \
        policy="$CADP_POLICY" \
        user="$CADP_USER"
else
    echo "cadp.properties not found, skipping migration for CADP."
fi

# CRDP
CRDP_PROP="$PROP_DIR/crdp.properties"
if [ -f "$CRDP_PROP" ]; then
    echo "found crdp.properties, migrating..."
    CRDP_ENDPOINT=$(get_prop "crdp_endpoint" "$CRDP_PROP")
    CRDP_TLS=$(get_prop "crdp_tls" "$CRDP_PROP")
    CRDP_POLICY=$(get_prop "crdp_policy" "$CRDP_PROP")
    CRDP_USER=$(get_prop "crdp_user_name" "$CRDP_PROP")
    CRDP_JWT=$(get_prop "crdp_jwt" "$CRDP_PROP")

    docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv put secret/crdp \
        endpoint="$CRDP_ENDPOINT" \
        tls="$CRDP_TLS" \
        policy="$CRDP_POLICY" \
        user="$CRDP_USER" \
        jwt="$CRDP_JWT"
else
    echo "crdp.properties not found, skipping migration for CRDP."
fi

echo "Step 3: Inject Secrets from Vault to Environment and Restart Tomcat"

# Fetch from Vault
echo "Fetching secrets from Vault..."
# We use 'vault kv get -field=key' which works in recent vault versions

export CADP_KEY_MANAGER_HOST=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=host secret/cadp)
export CADP_KEY_MANAGER_PORT=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=port secret/cadp)
export CADP_REGISTRATION_TOKEN=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=token secret/cadp)
export CADP_PROTECTION_POLICY_NAME=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=policy secret/cadp)
export CADP_USER_NAME=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=user secret/cadp)

export CRDP_ENDPOINT=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=endpoint secret/crdp)
export CRDP_TLS=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=tls secret/crdp)
export CRDP_POLICY=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=policy secret/crdp)
export CRDP_USER_NAME=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=user secret/crdp)
export CRDP_JWT=$(docker exec -e VAULT_TOKEN=roottoken demo_vault vault kv get -field=jwt secret/crdp)

echo "Secrets exported to environment."

echo "Restarting Tomcat with injected configuration..."
docker-compose up -d tomcat

echo "Migration Complete."
echo "You can check Vault UI at http://localhost:8200/ui using Token: roottoken"
