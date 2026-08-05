# OAuth2-Proxy in Grootschalige Productiesystemen

Dit document beschrijft hoe **OAuth2-Proxy** wordt toegepast binnen bedrijfskritische, high-traffic productieomgevingen (enterprise architecturen) met miljoenen gebruikers.

---

## 🚀 Waarom OAuth2-Proxy Enterprise-Ready is

Hoewel de configuratie via command-line vlaggen (`args`) complex kan aanvoelen, is de tool specifiek ontworpen voor cloud-native infrastructuren en biedt het drie cruciale productievoordelen:

### 1. Oneindige Horizontale Schaalbaarheid (Stateless)
* **Werking:** OAuth2-Proxy slaat standaard geen sessies op in een lokale database of bestandssysteem. Alle sessie- en tokeninformatie leeft versleuteld in de browser van de gebruiker (cookie-mode).
* **Productievoordeel:** Omdat de pods *stateless* zijn, kan elke willekeurige instantie in een Kubernetes-cluster een inkomend verzoek direct afhandelen. Via een `HorizontalPodAutoscaler` (HPA) kan de proxy-laag moeiteloos worden opgeschaald van 2 naar 100+ pods op basis van CPU/geheugendruk of netwerkverkeer, zonder dat gebruikers hun sessie verliezen.

### 2. High-Performance Engine (Go/Golang)
* **Werking:** De proxy is gebouwd in Go, dezelfde taal als Docker en Kubernetes. Go staat bekend om zijn extreem efficiënte afhandeling van gelijktijdige I/O-verzoeken (`goroutines`).
* **Productievoordeel:** Een enkele OAuth2-Proxy pod kan duizenden gelijktijdige netwerkverzoeken per seconde verwerken met een minimale voetafdruk (vaak slechts 20MB tot 50MB aan werkgeheugen). Dit minimaliseert de infrastructuurkosten bij miljoenen gebruikers.

### 3. Centrale Beveiliging voor Polyglot Architecturen
* **Werking:** In grote organisaties worden microservices in verschillende talen geschreven (bijv. Java/Quarkus, Node.js, Python, Go).
* **Productievoordeel:** In plaats van dat elk development team eigen code moet schrijven, testen en updaten om JWT-tokens te valideren, lost OAuth2-Proxy dit centraal op *vóór* het verkeer de applicatie bereikt. Applicatie-ontwikkelaars hoeven alleen eenvoudige HTTP-headers uit te lezen (bijv. `X-Auth-Request-User`).

---

## 📐 Productie Architectuurpatronen

In echte productiesystemen wordt OAuth2-Proxy zelden als een losse container per app ingezet. In plaats daarvan worden de volgende twee patronen toegepast:

### Patroon A: External Auth (Aanbevolen voor High-Traffic)
In dit scenario zit OAuth2-Proxy niet fysiek *tussen* de datastroom (data plane), maar fungeert het als een snelle side-channel validatie-service voor de Ingress Controller of API Gateway.

```text
Browser ──> NGINX Ingress / API Gateway ──(1. Auth Check?)──> OAuth2-Proxy Cluster
                 │                                                   │
          (3. HTTP 200 OK)                                   (2. JWT Validatie)
                 │                                                   │
                 ▼                                                   ▼
       Microservices (App1, App2)                             Keycloak / IAM
```

1. De **Ingress Controller** ontvangt het verzoek van de browser.
2. NGINX vraagt via een intern sub-verzoek (`auth_request` module) aan OAuth2-Proxy: *"Is dit verzoek legitiem?"*.
3. OAuth2-Proxy controleert de cookie/token en geeft een snelle `200 OK` (of `401 Unauthorized`) terug.
4. Bij een `200 OK` stuurt NGINX het verkeer direct door naar de backend. De proxy belast de eigenlijke applicatiestroom dus niet.

### Patroon B: Redis Session Storage (Voor Enterprise JWT's)
Wanneer gebruikers veel rollen, rechten en groepen hebben binnen Keycloak, worden de JWT-tokens te groot voor browser-cookies. Dit veroorzaakt `502 Bad Gateway` of `400 Bad Request` fouten in firewalls en load balancers.

* **De Productieoplossing:** OAuth2-Proxy wordt gekoppeld aan een **Redis-cluster**.
* **Werking:** Het zware JWT-token wordt opgeslagen in de supersnelle in-memory Redis-database. De browser krijgt alleen een klein, willekeurig en versleuteld sessie-ID mee (`session ticket`).
* **Configuratie-voorbeeld (Production):**
  ```yaml
  - --session-store-type=redis
  - --redis-connection-url=redis://redis-cluster.production.svc.cluster.local:6379
  ```

---

## 🔒 Security Best Practices in Productie

Voor een veilige uitrol in productieomgevingen moeten de volgende vlaggen strenger worden ingesteld dan in een lokale testomgeving:

| Vlag | Testomgeving (Kind/Dev) | Productieomgeving (Prod) | Reden |
| :--- | :--- | :--- | :--- |
| `--cookie-secure` | `false` | `true` | Dwingt af dat sessie-cookies *alleen* over versleutelde HTTPS-verbindingen worden verstuurd. |
| `--insecure-oidc-skip-issuer-verification` | `true` | `false` | Verplicht de proxy om te controleren of de Keycloak-issuer exact matcht met het SSL-certificaat. |
| `--cookie-httponly` | *Niet opgegeven* | `true` | Voorkomt dat kwaadaardige JavaScript-scripts (XSS) de sessie-cookie uit de browser kunnen stelen. |
| `--cookie-same-site` | *Niet opgegeven* | `lax` of `strict` | Beschermt gebruikers tegen Cross-Site Request Forgery (CSRF) aanvallen. |
