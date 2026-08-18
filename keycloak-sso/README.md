# Microservice Security & Single Sign-On (SSO) met Keycloak & OAuth2-Proxy

Dit project bevat de infrastructuur- en beveiligingsconfiguratie voor een gedistribueerde microservice-architectuur binnen een Kubernetes (Kind) cluster. De kern van de beveiliging wordt gevormd door de combinatie van **Keycloak** en **OAuth2-Proxy**.

---

## 🏛️ De Rollen binnen de Architectuur

Om applicaties (`app1` en `app2`) taalonafhankelijk en centraal te beveiligen, is er gekozen voor een strikte scheiding van taken tussen de **Identity Provider (IAM)** en de **Security Gateway (BFF)**.

```text
       [ Externe Browser-omgeving ]              │       [ Interne Kubernetes Netwerklaag ]
                                                 │
  1. Surf naar http://app1.local ───────────────┼───> NGINX Ingress Controller (Poort 80)
                                                 │                 │
                                                 │         (Stille Auth Check)
                                                 │                 ▼
  3. Toon Inlogscherm <─── (Browser Redirect) ──┼─────── OAuth2-Proxy Pod (Poort 4180) [BFF]
         │                                       │                 │
     (Inloggen)                                  │         (OIDC Discovery & Token Exchange)
         ▼                                       │                 ▼
  4. Valideer Sessie ────────────────────────────┼──────────> Keycloak Pod (Poort 8080)
                                                 │
  5. Succes! Toon App 1 (HTTP 200) <─────────────┼─────────── [ static://200 / Backend App ]
```

### 1. De Rol van Keycloak (Identity Provider)
**Keycloak** fungeert binnen dit project als de centrale **Identity Provider (IdP)** en de absolute *Source of Truth* voor identiteiten.
* **Gebruikers- en Cliëntenbeheer:** Keycloak beheert alle gebruikersaccounts, realms, wachtwoorden en API-cliënten (`oauth2-proxy-app1`, enz.).
* **Authenticatie & Token Generatie:** Zodra een gebruiker inlogt, valideert Keycloak de inloggegevens en genereert cryptografisch gesigneerde **JSON Web Tokens (JWT)**: het *ID-token* (wie ben je?), het *Access-token* (wat mag je?) en het *Refresh-token*.
* **Centrale SSO-Sessie:** Keycloak houdt een centrale sessie-cookie bij op `keycloak.local`. Hierdoor hoeft een gebruiker die al is ingelogd op App 2, bij het openen van App 1 niet opnieuw zijn wachtwoord in te voeren.

### 2. De Rol van OAuth2-Proxy (Security Gateway / Backend For Frontend)
**OAuth2-Proxy** fungeert als de **beveiligings-bodyguard** (Reverse Proxy) die direct *vóór* de daadwerkelijke applicaties is geplaatst. Het implementeert hiermee het **Backend For Frontend (BFF)** patroon:

* **Het BFF-Patroon (Token-afscherming):** In een puur frontend-landschap bewaart de browser tokens vaak onveilig in `localStorage`. Als **BFF-gateway** lost OAuth2-Proxy dit op: het vangt het rauwe JWT-token van Keycloak aan de backend-zijde op en verpakt dit in een zware, versleutelde, `HTTP-Only` en `Secure` browser-cookie. Omdat de browser (frontend) het token zelf nooit te zien krijgt, elimineert dit BFF-mechanisme het risico op token-diefstal via Cross-Site Scripting (XSS).
* **Ontkoppeling van Applicatie-code:** Doordat de proxy als centrale Security Gateway alle OIDC-logica en token-afhandeling overneemt, hoeven onze backend-applicaties (zoals Java/Quarkus of Node.js) zelf **geen** ingewikkelde inlogcode te bevatten. De gateway laat alleen legitiem verkeer door.
* **BFF-Header Injectie:** Zodra een verzoek via de cookie is goedgekeurd, vist de BFF-gateway het JWT-token intern weer uit de cookie. De proxy injecteert dit token vervolgens als een schone HTTP-header (`X-Auth-Request-Access-Token`) naar de achterliggende backend-app, zodat de applicatie direct weet welke specifieke gebruiker er binnenkomt.

> Let op: dit pattern is niet geschikt voor Android Apps omdat Android WebView vaak de session cookies kwijt raakt en daarom wordt uitgelogd: Gebruik in dat geval JWT tokens in combinatie met de `Android Keystore / Secure Storage`
---

## ⚙️ Essentiële SSO & Productie Instellingen

Tijdens de inrichting zijn de volgende cruciale parameters geconfigureerd om een stabiele, enterprise-waardige SSO-loop te garanderen:

### 1. Het Split-Brain Netwerk (`--skip-oidc-discovery=false`)
De OAuth2-Proxy pods praten *intern* via de Kubernetes DNS met Keycloak (`--oidc-issuer-url=http://cluster.local...`). Keycloak is op zijn beurt uitgerust met `KC_HOSTNAME=keycloak.local`, waardoor hij via de automatische OIDC-discovery aan de proxy dicteert dat de *browser* voor het inloggen altijd naar het externe Mac-adres (`keycloak.local`) moet worden gestuurd.

### 2. Cookie Isolatie (`--cookie-name` & `--cookie-domain`)
Omdat browsers cookies op een kaal `.local` domein blokkeren, en om te voorkomen dat applicaties elkaars sessies overschrijven (cookie-clash), heeft elke applicatie een strikt eigen scope gekregen:
* **App 1:** `--cookie-domain=app1.local` met `--cookie-name=_oauth2_proxy_app1`
* **App 2:** `--cookie-domain=app2.local` met `--cookie-name=_oauth2_proxy_app2`

### 3. Geruisloze SSO Doorloop (`--skip-provider-button=true`)
Om te voorkomen dat gebruikers bij elke app handmatig op een "Sign in with OIDC" knop moeten klikken, dwingt `--skip-provider-button=true` de proxy om de browser direct naar Keycloak te dirigeren. Omdat de dwingende `--prompt=login` vlag is weggelaten, herkent Keycloak die bestaande sessie onmiddellijk en flitst de gebruiker volledig automatisch door naar de app.

### 4. NGINX Buffer Annotations
Omdat JWT-tokens van Keycloak erg zwaar zijn en de cookies de standaard HTTP-header limieten van NGINX overschrijden, is het Ingress-manifest uitgebreid met grotere proxy-buffers om `502 Bad Gateway` fouten te voorkomen:
```yaml
metadata:
  annotations:
    nginx.ingress.kubernetes.io/proxy-buffer-size: "16k"
    nginx.ingress.kubernetes.io/proxy-buffers: "4 16k"
```

---

## 🚀 Toepassing in Productie

In grootschalige productieomgevingen draait exact deze opstelling. Bij extreem hoge bezoekersaantallen wordt OAuth2-Proxy horizontaal opgeschaald naar tientallen pods. Daarnaast kan de stateless cookie-modus van deze BFF-gateway eenvoudig worden uitgebreid naar een **Redis-cluster**-koppeling (`--session-store-type=redis`), waarbij de zware tokens in-memory in het cluster worden bewaard en de browser alleen een klein sessie-ticket meestuurt.

---

## 🚀 Aan de slag (Demo Setup)

Wil je deze complete SSO-omgeving stap-voor-stap zelf lokaal opzetten in een Kubernetes cluster?

Volg de volledige installatie-, configuratie- en instructiehandleiding in:
👉 **[doc/01-setup-keycloak-sso-demo.md](doc/01-setup-keycloak-sso-demo.md)**

Volg deze handleiding om Keycloak users uit LDAP te laten lezen:
👉 **[doc/02-add-ldap-integration.md](doc/02-add-ldap-integration.md)**
