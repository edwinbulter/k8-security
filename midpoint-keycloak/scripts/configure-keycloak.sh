#!/bin/bash
set -e

KEYCLOAK_URL="http://keycloak-midpoint.localhost"
REALM="MidpointRealm"
ADMIN_USER="admin"
ADMIN_PASS="adminpassword"

echo "=== Configuring Keycloak for midPoint demo ==="

# Wait for Keycloak to be ready
echo "Waiting for Keycloak to be ready..."
until curl -s "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; do
  sleep 2
done
echo "Keycloak is ready."

# Get admin token
TOKEN=$(curl -s "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=${ADMIN_USER}" \
  -d "password=${ADMIN_PASS}" \
  -d "grant_type=password" | jq -r '.access_token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to get admin token"
  exit 1
fi

# 1. Create realm
echo "Creating realm ${REALM}..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"realm\":\"${REALM}\",\"enabled\":true}" || true

# 2. Add User Storage Provider
echo "Adding User Storage Provider..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/components" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user-permissions-jpa",
    "providerId": "example-user-permissions-jpa",
    "providerType": "org.keycloak.storage.UserStorageProvider",
    "config": {
      "priority": ["0"],
      "fullSyncPeriod": ["-1"],
      "changedSyncPeriod": ["-1"]
    }
  }' || true

# 3. Create client-a
echo "Creating client-a..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "client-a",
    "name": "Web Client A",
    "enabled": true,
    "protocol": "openid-connect",
    "publicClient": false,
    "secret": "client-a-secret",
    "redirectUris": ["http://client-a-mid.localhost/*"],
    "webOrigins": ["http://client-a-mid.localhost"],
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": true
  }' || true

# 4. Create client-b
echo "Creating client-b..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "client-b",
    "name": "Web Client B",
    "enabled": true,
    "protocol": "openid-connect",
    "publicClient": false,
    "secret": "client-b-secret",
    "redirectUris": ["http://client-b-mid.localhost/*"],
    "webOrigins": ["http://client-b-mid.localhost"],
    "standardFlowEnabled": true,
    "directAccessGrantsEnabled": true
  }' || true

# 5. Add Permission Protocol Mapper to client-a
echo "Adding Permission Protocol Mapper to client-a..."
CLIENT_A_ID=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=client-a" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].id')

# Remove existing mappers first
for MAPPER_ID in $(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_A_ID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[].id' 2>/dev/null); do
  curl -s -X DELETE "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_A_ID}/protocol-mappers/models/${MAPPER_ID}" \
    -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || true
done

curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_A_ID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"permissions","protocol":"openid-connect","protocolMapper":"permission-protocol-mapper","config":{"claim.name":"permissions","access.token.claim":"true","id.token.claim":"true","multivalued":"true"}}'

# 6. Add Permission Protocol Mapper to client-b
echo "Adding Permission Protocol Mapper to client-b..."
CLIENT_B_ID=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/clients?clientId=client-b" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].id')

for MAPPER_ID in $(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_B_ID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[].id' 2>/dev/null); do
  curl -s -X DELETE "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_B_ID}/protocol-mappers/models/${MAPPER_ID}" \
    -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || true
done

curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_B_ID}/protocol-mappers/models" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"permissions","protocol":"openid-connect","protocolMapper":"permission-protocol-mapper","config":{"claim.name":"permissions","access.token.claim":"true","id.token.claim":"true","multivalued":"true"}}'

echo ""
echo "=== Keycloak configuration complete ==="
echo "Realm: ${REALM}"
echo "Clients: client-a, client-b"
echo "User Storage Provider: example-user-permissions-jpa"
echo "Protocol Mapper: permission-protocol-mapper"
echo ""
echo "Verify: ${KEYCLOAK_URL}/admin/master/console/"
