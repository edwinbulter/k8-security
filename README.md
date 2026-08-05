# Security Platform: Zero-Trust Architectuur PoC

Dit project bevat twee onafhankelijke Proof of Concepts (PoCs) die samen de basis vormen voor een gelaagde beveiligingsarchitectuur binnen Kubernetes.

---

## 📂 Beschikbare Demo's

Het project is opgedeeld in twee specifieke lagen:

1. 👤 **[User Identity & SSO Demo](./keycloak-sso/README.md)**
    * **Componenten:** Keycloak & OAuth2-Proxy (BFF-patroon).
    * **Beveiligingsvraagstuk:** Identificatie en autorisatie van de menselijke gebruiker. Het regelt de authenticatie, Single Sign-On (SSO) en beschermt tegen token-diefstal (XSS) in de browser door tokens via cookies aan de achterzijde af te schermen.

2. 🤖 **[Workload Identity & Netwerk Demo](./service-mesh/README.md)**
    * **Componenten:** Istio Service Mesh & Envoy-sidecars.
    * **Beveiligingsvraagstuk:** Identificatie en versleuteling van communicatie tussen services onderling (machine-naar-machine). Het automatiseert de uitgifte van certificaten en dwingt **Strict mTLS** af op de netwerklaag.

---

## 🔐 De Samenwerking: Een Zero-Trust Keten

De combinatie van beide demo's zorgt voor een doorlopende verificatie op zowel gebruikersniveau als netwerkniveau:

```text
[ Menselijke Gebruiker ]
          │
  (Inloggen via Keycloak)
          ▼
   [ OAuth2-Proxy ]  <─── Verifieert de User Identity (Wie ben jij?)
          │
   (Injecteert JWT-token in HTTP-header)
          ▼
   [ Envoy Proxy A ] <─── Verifieert de Workload Identity (Mag deze pod communiceren?)
          │
    (Strict mTLS Tunnel)
          ▼
   [ Envoy Proxy B ]
          │
          ▼
    [ Backend App ]  <─── Controleert de rechten (Rollen & Scopes) uit het JWT-token
```

* **Zonder Service Mesh:** Wordt de gebruiker correct geïdentificeerd, maar is het interne netwerkverkeer tussen pods onderling niet versleuteld en ontbreekt daar de wederzijdse identificatie op transportniveau.
* **Zonder Keycloak / OAuth2-Proxy:** Is de netwerklaag tussen de pods via mTLS beveiligd, maar ontbreekt de controle op de menselijke identiteit en rechten van de eindgebruiker die het verzoek via de browser start.

**Samen** zorgen ze ervoor dat elk verzoek op elk niveau wordt gecontroleerd. Dit sluit aan bij het uitgangspunt: *Never trust, always verify.*
