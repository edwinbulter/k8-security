# Setup Handleiding: midPoint + Keycloak Demo

Deze handleiding beschrijft stap-voor-stap hoe je de midPoint + Keycloak demo opzet op een Kind Kubernetes cluster.

## Vereisten

- `kubectl` CLI
- `docker` (voor het bouwen van de custom images)
- `kind` geïnstalleerd
- `jq` (voor het verwerken van JSON in het configuratiescript)
- Bestaand Kind cluster met ingress-nginx (zie `keycloak-userprovider/doc/01-setup-userprovider-demo.md` Stap 0)

> **Werkdirectory:** Alle commando's gaan ervan uit dat je werkt vanuit de `k8-security` projectmap.

## Stap 1: DNS Toevoegen

Voeg de nieuwe hostnames toe aan `/etc/hosts`:

```bash
echo "127.0.0.1   midpoint.localhost" | sudo tee -a /etc/hosts
echo "127.0.0.1   keycloak-midpoint.localhost" | sudo tee -a /etc/hosts
echo "127.0.0.1   client-a-mid.localhost" | sudo tee -a /etc/hosts
echo "127.0.0.1   client-b-mid.localhost" | sudo tee -a /etc/hosts
```

---

## Stap 2: PostgreSQL deployen

```bash
kubectl apply -f midpoint-keycloak/k8/01-postgres.yaml
```

Wacht tot PostgreSQL draait:

```bash
kubectl wait --for=condition=Available deployment/postgres -n midpoint-keycloak --timeout=60s
```

PostgreSQL bevat drie databases:
- `keycloak` — Keycloak's eigen opslag
- `midpoint` — midPoint's repository
- `userdb` — gedeelde database met users, roles, permissions tabellen

De `users` en `user_roles` tabellen zijn **leeg** — midPoint vult deze. De `roles`, `permissions` en `role_permissions` tabellen bevatten seed data.

## Stap 3: Docker Images Bouwen

### Keycloak image (met SPI JARs)

De Dockerfile hergebruikt de SPI broncode uit `keycloak-user-permissions-spi`:

```bash
# Bouw vanuit de k8-security root directory (zodat de Dockerfile de SPI source kan bereiken)
docker build -t keycloak-midpoint-spi:latest -f midpoint-keycloak/Dockerfile.keycloak .
```

### Web client image

```bash
docker build -t keycloak-midpoint-web-client:latest midpoint-keycloak/web-client/
```

### Load images into Kind

```bash
kind load docker-image keycloak-midpoint-spi:latest --name single-node
kind load docker-image keycloak-midpoint-web-client:latest --name single-node
```

## Stap 4: midPoint database initialiseren

Het midPoint schema moet handmatig in de `midpoint` database worden geladen met het native PostgreSQL SQL script:

```bash
# Kopieer het SQL script uit de midPoint image naar lokaal
kubectl cp midpoint-keycloak/$(kubectl get pod -n midpoint-keycloak -l app=midpoint -o jsonpath='{.items[0].metadata.name}'):/opt/midpoint/doc/config/sql/native/postgres.sql /tmp/midpoint-postgres.sql

# Laad het schema in de midpoint database
kubectl cp /tmp/midpoint-postgres.sql midpoint-keycloak/$(kubectl get pod -n midpoint-keycloak -l app=postgres -o jsonpath='{.items[0].metadata.name}'):/tmp/midpoint-postgres.sql
kubectl exec deployment/postgres -n midpoint-keycloak -- psql -U postgres -d midpoint -f /tmp/midpoint-postgres.sql
```

> **Let op:** Dit script moet worden uitgevoerd nadat de midPoint image minimaal één keer is gestart (zodat de pod bestaat en het SQL bestand beschikbaar is). Als midPoint nog niet is gedeployed, deploy hem dan eerst, wacht tot de pod draait, en voer dan deze stap uit.

## Stap 5: midPoint (her)deployen

```bash
kubectl apply -f midpoint-keycloak/k8/02-midpoint.yaml
kubectl delete pod -l app=midpoint -n midpoint-keycloak
```

Wacht tot midPoint draait:

```bash
kubectl wait --for=condition=Available deployment/midpoint -n midpoint-keycloak --timeout=120s
```

midPoint is toegankelijk via: http://midpoint.localhost

**Inloggen:**
- Gebruiker: `administrator`
- Wachtwoord: `5ecr3t`

## Stap 6: Keycloak deployen

```bash
kubectl apply -f midpoint-keycloak/k8/03-keycloak.yaml
```

Wacht tot Keycloak draait:

```bash
kubectl wait --for=condition=Available deployment/keycloak -n midpoint-keycloak --timeout=120s
```

Keycloak is toegankelijk via: http://keycloak-midpoint.localhost

**Inloggen:**
- Gebruiker: `admin`
- Wachtwoord: `adminpassword`

## Stap 7: Keycloak configureren

Voer het automatiseringsscript uit:

```bash
chmod +x midpoint-keycloak/scripts/configure-keycloak.sh
./midpoint-keycloak/scripts/configure-keycloak.sh
```

Dit script doet het volgende:
1. Realm `MidpointRealm` aanmaken
2. User Storage Provider `example-user-permissions-jpa` toevoegen
3. Clients `client-a` en `client-b` aanmaken met redirect URIs
4. Permission Protocol Mapper toevoegen aan beide clients

**Verifieer de configuratie:**
- Ga naar http://keycloak-midpoint.localhost
- Log in met `admin` / `adminpassword`
- Selecteer realm `MidpointRealm`
- Controleer bij **User Federation** dat `user-permissions-jpa` aanwezig is
- Controleer bij **Clients** dat `client-a` en `client-b` aanwezig zijn

## Stap 8: Web clients deployen

```bash
kubectl apply -f midpoint-keycloak/k8/04-web-clients.yaml
```

Wacht tot de web clients draaien:

```bash
kubectl wait --for=condition=Available deployment/web-client-a -n midpoint-keycloak --timeout=60s
kubectl wait --for=condition=Available deployment/web-client-b -n midpoint-keycloak --timeout=60s
```

Web clients:
- Client A: http://client-a-mid.localhost
- Client B: http://client-b-mid.localhost

## Stap 9: midPoint configureren

### 9.1 PostgreSQL connector toevoegen

midPoint heeft een PostgreSQL JDBC driver nodig. De officiële midPoint Docker image bevat deze al.

### 9.2 Resource aanmaken

1. Ga naar http://midpoint.localhost
2. Log in met `administrator` / `5ecr3t`
3. Ga naar **Configuration** → **Import Objects**
4. Importeer het bestand `midpoint/config/resource-postgresql.xml` (vink **Overwrite existing object** aan als je dit al eerder hebt gedaan)
5. Ga naar **Resources** → verify dat de resource `PostgreSQL UserDB` groen is

### 9.3 Rollen aanmaken in midPoint

Voordat je rollen kunt toewijzen aan gebruikers, moet je ze eerst in midPoint aanmaken:

1. Ga naar **Configuration** → **Import Objects**
2. Importeer het bestand `midpoint/config/roles.xml`
3. Ga naar **Roles** → **All roles** en controleer of `role-1` t/m `role-4` aanwezig zijn.

### 9.4 Gebruikers aanmaken in midPoint

Maak drie testgebruikers aan in midPoint:

**Gebruiker 1:**
1. Ga naar **Users** → **New user**
2. Name: `user-1`
3. Email: `user-1@example.com`
4. Extension attributes:
   - `id`: `a1b2c3d4-0001-4000-8000-000000000001`
   - `password`: `hello-user-1`
   - `phone`: `0612345671`
5. Save

**Gebruiker 2:**
- Name: `user-2`
- Email: `user-2@example.com`
- `id`: `a1b2c3d4-0002-4000-8000-000000000002`
- `password`: `hello-user-2`
- `phone`: `0612345672`

**Gebruiker 3:**
- Name: `user-3`
- Email: `user-3@example.com`
- `id`: `a1b2c3d4-0003-4000-8000-000000000003`
- `password`: `hello-user-3`
- `phone`: `0612345673`

### 9.5 Rollen toewijzen in midPoint

Wijs rollen toe aan de gebruikers door role assignments toe te voegen:

- `user-1` → `role-1` (levert permission-1, permission-2, permission-3)
- `user-2` → `role-2` (levert permission-4, permission-5, permission-6)
- `user-3` → `role-1` + `role-2` (levert permission-1 t/m permission-6)

### 9.6 Provisioning uitvoeren

1. Ga naar **Resources** → `PostgreSQL UserDB` → **Accounts**
2. Controleer dat de gebruikers zijn geprovisioneerd
3. Verifieer direct in PostgreSQL:

```bash
kubectl exec deployment/postgres -n midpoint-keycloak -- psql -U postgres -d userdb -c "SELECT * FROM users;"
kubectl exec deployment/postgres -n midpoint-keycloak -- psql -U postgres -d userdb -c "SELECT * FROM user_roles;"
```

## Stap 10: Testen

### 10.1 Inloggen via Client A

1. Open http://client-a-mid.localhost in je browser
2. Je wordt doorgestuurd naar Keycloak login
3. Log in met `user-1` / `hello-user-1`
4. Je ziet de permissies: `permission-1`, `permission-2`, `permission-3`

### 10.2 SSO testen

1. Open http://client-b-mid.localhost in dezelfde browser
2. Je bent automatisch ingelogd (SSO)
3. Je ziet dezelfde permissies

### 10.3 Rol wijzigen in midPoint

1. Ga naar midPoint → **Users** → `user-1`
2. Verwijder de `role-1` assignment
3. Voeg `role-2` toe
4. Wacht tot provisioning is voltooid
5. Log uit en opnieuw in via http://client-a-mid.localhost
6. Je ziet nu: `permission-4`, `permission-5`, `permission-6`

### 10.4 Gebruiker verwijderen in midPoint

1. Ga naar midPoint → **Users** → `user-3` → **Delete**
2. Wacht tot provisioning is voltooid
3. Probeer in te loggen met `user-3` / `hello-user-3` → dit moet falen

## Opruimen

```bash
kubectl delete namespace midpoint-keycloak
```

Dit verwijdert alles: PostgreSQL, midPoint, Keycloak, web clients, ingresses en de namespace.
