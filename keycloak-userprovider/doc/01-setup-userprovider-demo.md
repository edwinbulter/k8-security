# Keycloak User Storage Provider met PostgreSQL — Setup Handleiding

Deze handleiding beschrijft hoe je de custom Keycloak User Storage Provider uitrolt op een lokaal Kind Kubernetes cluster, inclusief een PostgreSQL database met 10 testgebruikers.

## Vereisten

- Een draaiend **Kind** Kubernetes cluster
- `kubectl` CLI
- `docker` (voor het bouwen van de custom Keycloak image)
- Toegang tot het cluster via `kind load docker-image`

---

## Stap 1: Custom Keycloak Docker Image Bouwen

De Dockerfile gebruikt een multi-stage build: Maven compileert de provider JAR, waarna deze in de Keycloak image wordt geplaatst.

```bash
# Bouw de image
docker build -t keycloak-userprovider:latest /Users/e.g.h.bulter/IdeaProjects/k8-security/keycloak-userprovider/

# Laad de image in het Kind cluster
kind load docker-image keycloak-userprovider:latest --name kind-multi-node-cluster
```

> **Let op:** Vervang `kind-multi-node-cluster` door de naam van jouw Kind cluster als deze anders heet. Controleer met `kind get clusters`.

---

## Stap 2: PostgreSQL Database Uitrollen

De PostgreSQL manifest bevat een ConfigMap met een init-SQL script dat automatisch de database `userdb` aanmaakt en vult met 10 gebruikers.

```bash
kubectl apply -f k8/01-postgres.yaml
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
kubectl apply -f k8/02-keycloak.yaml
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

## Stap 4: DNS Instellen

Zorg dat `keycloak.local` verwijst naar je lokale machine. Voeg toe aan `/etc/hosts`:

```text
127.0.0.1   keycloak.local
```

> **Let op:** Als de `keycloak-sso-demo` namespace ook draait met een Keycloak op `keycloak.local`, verwijder dan eerst die ingress of gebruik een andere hostnaam. Beide kunnen niet tegelijk op dezelfde host draaien.

---

## Stap 5: User Storage Provider Koppelen in Keycloak

1. Open je browser en ga naar `http://keycloak.local`.
2. Log in met `admin` / `adminpassword`.
3. Klik linksboven op **Master** → **Create Realm**. Noem de realm: `DemoRealm`.
4. Ga in het linkermenu naar **User Federation**.
5. Klik op **Add provider** — je zou **`example-user-storage-jpa`** moeten zien in de lijst.
6. Selecteer **`example-user-storage-jpa`**.
7. Laat de instellingen op hun standaardwaarden en klik op **Save**.

> De provider is nu actief. Keycloak zal gebruikers uit de PostgreSQL database opzoeken wanneer er een inlogpoging of zoekopdracht plaatsvindt.

---

## Stap 6: Gebruikers Bekijken in de Admin Console

1. Ga in het linkermenu naar **Users**.
2. Klik op **View all users** of laat het zoekveld leeg en klik op **Search**.
3. Je ziet nu de 10 federated gebruikers: `user-1` t/m `user-10`.
4. Klik op een gebruiker om de details te bekijken (username, email, attributen).

> De gebruikers hebben een badge *Federated* — dit geeft aan dat ze uit de externe PostgreSQL database komen en niet in Keycloak's eigen opslag zijn geregistreerd.

---

## Stap 7: Inloggen Testen

1. Ga naar **Users** → klik op `user-1` → tabblad **Credentials**.
2. Klik op **Set password** en vul `hello-user-1` in (zet *Temporary* uit).
   > Dit werkt alleen als de provider *writable* is. In deze demo is de provider read-write, maar het wachtwoord wordt in de PostgreSQL database opgeslagen, niet in Keycloak's credential store.
3. **Alternatieve test (aanbevolen):** Maak een test client aan en log in via de Keycloak Account Console:
   - Ga naar **Clients** → **Create client** → Client ID: `test-app` → **Save**.
   - Ga naar **Access settings** → **Valid redirect URIs**: `http://localhost:8080/*` → **Save**.
   - Open `http://keycloak.local/realms/DemoRealm/account/` in een incognito venster.
   - Log in met `user-1` / `hello-user-1`.
   - Als het wachtwoord klopt, word je doorgestuurd naar de Keycloak Account Console.

---

## Stap 8: Opruimen

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
Controleer of de datasource `user-store` correct is geconfigureerd:
```bash
kubectl logs deployment/keycloak -n keycloak-userprovider | grep -i "user-store"
```

### PostgreSQL verbinding mislukt
Controleer of PostgreSQL bereikbaar is vanuit de Keycloak pod:
```bash
kubectl exec -it deployment/keycloak -n keycloak-userprovider -- nc -zv postgres 5432
```

### Datasource niet geregistreerd in Keycloak
Keycloak 24 (Quarkus) registreert datasources via environment variables met het prefix `QUARKUS_DATASOURCE_`. De datasource naam `user-store` komt overeen met de `hibernate.connection.datasource` property in `persistence.xml`. Als de datasource niet verschijnt, controleer dan of de PostgreSQL JDBC driver beschikbaar is in de Keycloak image (deze is standaard aanwezig in `quay.io/keycloak/keycloak:24.0.0`).
