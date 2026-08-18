# Keycloak User Storage Provider met PostgreSQL — Setup Handleiding

Deze handleiding beschrijft hoe je de custom Keycloak User Storage Provider uitrolt op een lokaal Kind Kubernetes cluster, inclusief een PostgreSQL database met 10 testgebruikers.

## Vereisten

- `kubectl` CLI
- `docker` (voor het bouwen van de custom Keycloak image)
- `kind` geïnstalleerd

> **Werkdirectory:** Alle commando's in deze handleiding gaan ervan uit dat je werkt vanuit de `k8-security` projectmap.

---

## Stap 0: Kind Cluster Aanmaken met Ingress Support

Kind heeft standaard geen ingress controller en geen poorten gemapt naar de host. Voor deze demo is poort 80 nodig om `keycloak.localhost` bereikbaar te maken vanuit de browser.

**Maak het cluster aan met extraPortMappings:**

```bash
cat <<EOF | kind create cluster --name single-node --config -
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    listenAddress: "127.0.0.1"
    protocol: TCP
EOF
```

**Installeer de NGINX Ingress Controller:**

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wacht tot de ingress controller ready is
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

**DNS instellen:**

macOS onderschept `.local` domeinen via mDNS (Bonjour), daarom gebruiken we `.localhost`:

```bash
echo "127.0.0.1   keycloak.localhost" | sudo tee -a /etc/hosts
```

> **Let op:** Als je al een Kind cluster hebt draaien met de naam `single-node`, verwijder deze eerst met `kind delete cluster --name single-node`.

---

## Stap 1: Custom Keycloak Docker Image Bouwen

De Dockerfile gebruikt een multi-stage build: Maven compileert de provider JAR, waarna deze in de Keycloak image wordt geplaatst.

```bash
# Bouw de image
docker build -t keycloak-userprovider:latest keycloak-userprovider/

# Laad de image in het Kind cluster
kind load docker-image keycloak-userprovider:latest --name single-node
```

> **Let op:** Gebruik de naam van jouw Kind cluster. Controleer met `kind get clusters`.

---

## Stap 2: PostgreSQL Database Uitrollen

De PostgreSQL manifest bevat een ConfigMap met een init-SQL script dat automatisch de database `userdb` aanmaakt en vult met 10 gebruikers.

```bash
kubectl apply -f keycloak-userprovider/k8/01-postgres.yaml
```

**Controleer of PostgreSQL draait:**
```bash
kubectl get pods -n keycloak-userprovider -l app=postgres
```

Wacht tot de pod status `Running` is.

**Verifieer de testgebruikers in de database:**
```bash
POSTGRES_POD=$(kubectl get pod -l app=postgres -n keycloak-userprovider -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $POSTGRES_POD -n keycloak-userprovider -- psql -U postgres -d userdb -c "SELECT username, email, password FROM users;"
```

Je zou het volgende resultaat moeten zien:

```text
 username  |        email        |    password
-----------+---------------------+----------------
 user-1    | user-1@example.com  | hello-user-1
 user-2    | user-2@example.com  | hello-user-2
 user-3    | user-3@example.com  | hello-user-3
 ...
 user-10   | user-10@example.com | hello-user-10
(10 rows)
```

---

## Stap 3: Keycloak Uitrollen

```bash
kubectl apply -f keycloak-userprovider/k8/02-keycloak.yaml
```

**Controleer of Keycloak draait:**
```bash
kubectl get pods -n keycloak-userprovider -l app=keycloak
```

Wacht tot de pod status `Running` is en de logboeken aangeven dat Keycloak is gestart:

```bash
kubectl logs -f deployment/keycloak -n keycloak-userprovider
```

Zoek naar de regel `Keycloak 24.0.0 started in...`.

---

## Stap 4: User Storage Provider Koppelen in Keycloak

1. Open je browser en ga naar `http://keycloak.localhost`.
2. Log in met `admin` / `adminpassword`.
3. Klik linksboven op **Master** → **Create Realm**. Noem de realm: `DemoRealm`.
4. Ga in het linkermenu naar **User Federation**.
5. Klik op **Add provider** — je zou **`example-user-storage-jpa`** moeten zien in de lijst.
6. Selecteer **`example-user-storage-jpa`**.
7. Laat de instellingen op hun standaardwaarden en klik op **Save**.

> De provider is nu actief. Keycloak zal gebruikers uit de PostgreSQL database opzoeken wanneer er een inlogpoging of zoekopdracht plaatsvindt.

---

## Stap 5: Gebruikers Bekijken in de Admin Console

1. Ga in het linkermenu naar **Users**.
2. Vul `*` in het zoekveld in en klik op **Search**.
   > In Keycloak 24 is de Search-knop disabled bij een leeg zoekveld. Gebruik `*` als wildcard om alle gebruikers te tonen.
3. Je ziet nu de 10 federated gebruikers: `user-1` t/m `user-10`.
4. Klik op een gebruiker om de details te bekijken (username, email, attributen).

> De gebruikers hebben een badge *Federated* — dit geeft aan dat ze uit de externe PostgreSQL database komen en niet in Keycloak's eigen opslag zijn geregistreerd.

---

## Stap 6: Inloggen Testen

1. Open `http://keycloak.localhost/realms/DemoRealm/account/` in een incognito venster.
2. Log in met `user-1` / `hello-user-1`.
3. Als het wachtwoord klopt, word je doorgestuurd naar de Keycloak Account Console.

> De wachtwoorden staan in de PostgreSQL database (kolom `password`). De provider valideert deze via de `isValid` methode. Geen extra configuratie nodig in Keycloak.

---

## Stap 7: Opruimen

Na het testen kun je alles met één command verwijderen:

```bash
kubectl delete namespace keycloak-userprovider
```

Dit verwijdert de namespace inclusief PostgreSQL, Keycloak, alle pods, services en ingresses. De custom Docker image blijft lokaal beschikbaar en kan worden verwijderd met:

```bash
docker rmi keycloak-userprovider:latest
```

---

## Probleemoplossing

### Keycloak start niet — Provider niet gevonden
Controleer of de JAR in de image zit:
```bash
docker run --rm keycloak-userprovider:latest ls /opt/keycloak/providers/
```
Je zou `user-storage-jpa-example.jar` moeten zien.

### Geen gebruikers zichtbaar in Admin Console

**Controleer of de datasource `user-store` correct is geconfigureerd:**
```bash
kubectl logs deployment/keycloak -n keycloak-userprovider | grep -i "user-store"
```

**Controleer of de provider wordt aangeroepen:**
Zet `hibernate.show_sql=true` in `persistence.xml`, herbouw de image, en zoek naar users in de admin console. In de logs zou je een SQL query op de `users` tabel moeten zien.

**Controleer of de REST API users retourneert:**
```bash
TOKEN=$(curl -s http://keycloak.localhost/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=adminpassword" \
  -d "grant_type=password" | jq -r '.access_token')

curl -s "http://keycloak.localhost/admin/realms/DemoRealm/users?first=0&max=20&search=*" \
  -H "Authorization: Bearer $TOKEN" | jq '. | length'
```
Dit moet `10` retourneren. Als de API `0` retourneert met `search=*` maar wel users retourneert zonder search parameter, controleer dan of de `searchForUserStream` methode `*` naar `%` converteert.

**Admin console toont geen users maar API wel:**
Dit kan komen door een verlopen browser sessie. Open een incognito/privé venster en log opnieuw in.

### PostgreSQL verbinding mislukt
Controleer of PostgreSQL bereikbaar is vanuit de Keycloak pod:
```bash
kubectl exec -it deployment/keycloak -n keycloak-userprovider -- nc -zv postgres 5432
```

### Datasource niet geregistreerd in Keycloak
Keycloak 24 (Quarkus) registreert de custom datasource `user-store` via `quarkus.properties` in de Docker image. De persistence unit in `persistence.xml` gebruikt `hibernate.connection.datasource=user-store` om de datasource te koppelen. De default Keycloak datasource wordt via `KC_DB=postgres` environment variables geconfigureerd. Beide moeten PostgreSQL gebruiken zodat de Quarkus PostgreSQL extensie geladen wordt.

> **Belangrijk:** Gebruik `quarkus.datasource.user-store.jdbc.transactions=xa` (niet `enabled`) voor de user-store datasource. XA transacties vereisen `max_prepared_transactions=100` op PostgreSQL.

### Ingress niet bereikbaar (404 of connection refused)
Controleer of de NGINX ingress controller draait:
```bash
kubectl get pods -n ingress-nginx
```
Controleer of poort 80 gemapt is op het Kind cluster:
```bash
docker port single-node-control-plane
```
Je zou `80/tcp -> 127.0.0.1:80` moeten zien. Zo niet, hercreëer het cluster met de `extraPortMappings` uit Stap 0.
