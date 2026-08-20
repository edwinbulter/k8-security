# Keycloak User Permissions SPI Demo — Setup Handleiding

Deze handleiding beschrijft hoe je een custom Keycloak Protocol Mapper uitrolt die permissions uit een externe PostgreSQL database als JWT claims injecteert, inclusief twee Node.js web clients met SSO.

## Vereisten

- `kubectl` CLI
- `docker` (voor het bouwen van de custom images)
- `kind` geïnstalleerd
- Bestaand Kind cluster met ingress-nginx (zie `keycloak-userprovider/doc/01-setup-userprovider-demo.md` Stap 0)

> **Werkdirectory:** Alle commando's gaan ervan uit dat je werkt vanuit de `k8-security` projectmap.

---

## Stap 1: DNS Toevoegen

Voeg de nieuwe hostnames toe aan `/etc/hosts`:

```bash
echo "127.0.0.1   keycloak-permissions.localhost" | sudo tee -a /etc/hosts
echo "127.0.0.1   client-a.localhost" | sudo tee -a /etc/hosts
echo "127.0.0.1   client-b.localhost" | sudo tee -a /etc/hosts
```

---

## Stap 2: Docker Images Bouwen

### Keycloak image (met User Storage Provider + Permission Protocol Mapper)

```bash
docker build -t keycloak-permissions-spi:latest keycloak-user-permissions-spi/
kind load docker-image keycloak-permissions-spi:latest --name single-node
```

### Web client image

```bash
docker build -t keycloak-permissions-web-client:latest keycloak-user-permissions-spi/web-client/
kind load docker-image keycloak-permissions-web-client:latest --name single-node
```

---

## Stap 3: PostgreSQL Database Uitrollen

De PostgreSQL manifest bevat init SQL voor 5 tabellen: `users`, `roles`, `permissions`, `role_permissions`, `user_roles` — gevuld met 10 users, 4 roles en 10 permissions.

```bash
kubectl apply -f keycloak-user-permissions-spi/k8/01-postgres.yaml
```

**Controleer of PostgreSQL draait:**
```bash
kubectl get pods -n keycloak-permissions -l app=postgres
```

**Verifieer de data:**
```bash
POSTGRES_POD=$(kubectl get pod -l app=postgres -n keycloak-permissions -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POSTGRES_POD -n keycloak-permissions -- psql -U postgres -d userdb -c "
SELECT u.username, r.name as role, p.name as permission
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
ORDER BY u.username, p.name;
"
```

---

## Stap 4: Keycloak Uitrollen

```bash
kubectl apply -f keycloak-user-permissions-spi/k8/02-keycloak.yaml
```

**Controleer of Keycloak draait:**
```bash
kubectl get pods -n keycloak-permissions -l app=keycloak
```

Wacht tot je in de logs ziet: `Keycloak 24.0.0 started in...`

```bash
kubectl logs -f deployment/keycloak -n keycloak-permissions
```

---

## Stap 5: Web Clients Uitrollen

```bash
kubectl apply -f keycloak-user-permissions-spi/k8/03-web-clients.yaml
```

**Controleer of beide clients draaien:**
```bash
kubectl get pods -n keycloak-permissions -l app=web-client-a
kubectl get pods -n keycloak-permissions -l app=web-client-b
```

---

## Stap 6: Keycloak Configureren

1. Open `http://keycloak-permissions.localhost` in je browser.
2. Log in met `admin` / `adminpassword`.

### 6.1 Realm aanmaken
3. Klik linksboven op **Master** → **Create Realm**. Noem de realm: `PermissionsRealm`.

### 6.2 User Storage Provider toevoegen
4. Ga naar **User Federation**.
5. Klik **Add provider** → selecteer **`example-user-permissions-jpa`**.
6. Klik **Save**.

### 6.3 Clients aanmaken

**Client A:**
7. Ga naar **Clients** → **Create client**.
   - Client ID: `client-a`
   - Naam: `Web Client A`
   - Klik **Next**
8. Op de **Capability config** pagina:
   - Zet **Client authentication** aan (naar "Confidential access")
   - Klik **Next**
9. Op de **Login and logout** pagina:
   - **Valid redirect URIs**: `http://client-a.localhost/*`
   - **Valid post logout redirect URIs**: `http://client-a.localhost/*`
   - **Web origins**: `http://client-a.localhost`
   - Klik **Save**
10. Ga naar de **Credentials** tab → kopieer het **Client secret**.
11. Update het secret in het Kubernetes manifest als het afwijkt van `client-a-secret`:
    ```bash
    kubectl set env deployment/web-client-a CLIENT_SECRET=<jouw-secret> -n keycloak-permissions
    kubectl rollout restart deployment/web-client-a -n keycloak-permissions
    ```

**Client B:**
12. Herhaal stappen 7-11 voor `client-b` met:
    - Client ID: `client-b`
    - Redirect URIs: `http://client-b.localhost/*`
    - Secret: `client-b-secret`

### 6.4 Permission Protocol Mapper toevoegen

In Keycloak 24 is de "Add mapper" knop niet zichtbaar in de Client scopes UI. Gebruik de Admin REST API om de mapper toe te voegen.

**Voer uit in een terminal:**

```bash
# Haal admin token op
TOKEN=$(curl -s http://keycloak-permissions.localhost/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=adminpassword" \
  -d "grant_type=password" | jq -r '.access_token')

# Voeg Permission Mapper toe aan client-a
CLIENT_A_ID=$(curl -s "http://keycloak-permissions.localhost/admin/realms/PermissionsRealm/clients?clientId=client-a" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

curl -s -X POST "http://keycloak-permissions.localhost/admin/realms/PermissionsRealm/clients/$CLIENT_A_ID/protocol-mappers/models" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"permissions","protocol":"openid-connect","protocolMapper":"permission-protocol-mapper","config":{"permissions":"permissions","included.in.access.token":"true","included.in.id.token":"true"}}'

# Voeg Permission Mapper toe aan client-b
CLIENT_B_ID=$(curl -s "http://keycloak-permissions.localhost/admin/realms/PermissionsRealm/clients?clientId=client-b" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')

curl -s -X POST "http://keycloak-permissions.localhost/admin/realms/PermissionsRealm/clients/$CLIENT_B_ID/protocol-mappers/models" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"permissions","protocol":"openid-connect","protocolMapper":"permission-protocol-mapper","config":{"permissions":"permissions","included.in.access.token":"true","included.in.id.token":"true"}}'
```

**Verifieer dat de mappers zijn toegevoegd:**

```bash
curl -s "http://keycloak-permissions.localhost/admin/realms/PermissionsRealm/clients/$CLIENT_A_ID/protocol-mappers/models" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Je zou een mapper met `"protocolMapper": "permission-protocol-mapper"` moeten zien.

### 6.5 Federated users verifiëren

17. Ga naar **Users** → vul `*` in het zoekveld → klik **Search**.
18. Je zou `user-1` t/m `user-10` moeten zien.

---

## Stap 7: Testen met SSO

### Test 1: Inloggen en permissions bekijken

1. Open `http://client-a.localhost` in een incognito venster.
2. Je wordt doorgestuurd naar Keycloak → log in met `user-1` / `hello-user-1`.
3. Je ziet nu **Web Client A** met de gebruikersnaam `user-1` en de permissions:
   - `permission-1`
   - `permission-2`
   - `permission-3`

### Test 2: SSO — tweede client zonder opnieuw in te loggen

4. Open `http://client-b.localhost` in **hetzelfde browser venster** (nieuw tabblad).
5. Je wordt automatisch ingelogd (geen login scherm) — SSO werkt!
6. Je ziet **Web Client B** met dezelfde gebruiker `user-1` en dezelfde permissions.

### Test 3: Verschillende gebruikers met meerdere roles

7. Log uit (ga naar `http://client-a.localhost/logout`).
8. Log in met `user-5` / `hello-user-5` (heeft role-1 + role-2).
9. Verwacht: `permission-1` t/m `permission-6`.

### Test 4: Gebruiker met roles die geen permissions delen

10. Log uit en log in met `user-10` / `hello-user-10` (heeft role-1 + role-4).
11. Verwacht: `permission-1`, `permission-2`, `permission-3`, `permission-9`, `permission-10`.

---

## Stap 8: Opruimen

```bash
kubectl delete namespace keycloak-permissions
```

Dit verwijdert de namespace inclusief PostgreSQL, Keycloak, beide web clients, alle pods, services en ingresses.

Docker images verwijderen:
```bash
docker rmi keycloak-permissions-spi:latest
docker rmi keycloak-permissions-web-client:latest
```

---

## Probleemoplossing

### Permission Mapper verschijnt niet in Keycloak
Controleer of de mapper JAR in de image zit:
```bash
docker run --rm keycloak-permissions-spi:latest ls /opt/keycloak/providers/
```
Je zou `permission-protocol-mapper.jar` en `user-storage-jpa-example.jar` moeten zien.

### Geen permissions in JWT token
Controleer de Keycloak logs tijdens inloggen:
```bash
kubectl logs deployment/keycloak -n keycloak-permissions --since=1m 2>&1 | grep -i "Permission"
```
Je zou logregels moeten zien met `PermissionProtocolMapper: setClaim for user ...` en `Found permissions: [...]`.

### Web client toont "OIDC client not initialized"
De web client kan Keycloak niet bereiken. Controleer of Keycloak draait en de DNS correct is:
```bash
kubectl exec -it deployment/web-client-a -n keycloak-permissions -- wget -qO- http://keycloak-permissions.localhost/realms/PermissionsRealm/.well-known/openid-configuration | head -1
```

### SSO werkt niet
Zorg dat beide clients in dezelfde realm (`PermissionsRealm`) zitten en dat je hetzelfde browser venster gebruikt. Keycloak's sessie cookie zorgt voor SSO binnen dezelfde realm.

### Client secret mismatch
De client secrets in de Kubernetes manifests (`client-a-secret`, `client-b-secret`) moeten overeenkomen met de secrets in Keycloak. Haal de juiste secrets op via de Keycloak admin console → Clients → Credentials tab.
