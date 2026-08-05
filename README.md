# Security Platform: Zero-Trust Architectuur PoC

Dit project bevat drie onafhankelijke Proof of Concepts (PoCs) die samen de basis vormen voor een gelaagde beveiligingsarchitectuur binnen Kubernetes.

---

## 📂 Beschikbare Demo's

Het project is opgedeeld in drie specifieke lagen:

1. 👤 **[User Identity & SSO Demo](./keycloak-sso/README.md)**
    * **Componenten:** Keycloak & OAuth2-Proxy (BFF-patroon).
    * **Beveiligingsvraagstuk:** Identificatie en autorisatie van de menselijke gebruiker. Het regelt de authenticatie, Single Sign-On (SSO) en beschermt tegen token-diefstal (XSS) in de browser door tokens via cookies aan de achterzijde af te schermen.

2. 🤖 **[Workload Identity & Netwerk Demo](./service-mesh/README.md)**
    * **Componenten:** Istio Service Mesh & Envoy-sidecars.
    * **Beveiligingsvraagstuk:** Identificatie en versleuteling van communicatie tussen services onderling (machine-naar-machine). Het automatiseert de uitgifte van certificaten en dwingt **Strict mTLS** af op de netwerklaag.

3. 🔐 **[PKI-Driven Service Mesh Demo](./pki-service-mesh/README.md)**
    * **Componenten:** cert-manager (PKI) & Istio Service Mesh.
    * **Beveiligingsvraagstuk:** Organisatie-brede vertrouwensketen voor cross-cluster en hybride omgevingen. Het koppelt Istio aan een centrale PKI, waardoor certificaten herleidbaar zijn naar een overkoepelende autoriteit en systemen binnen én buiten Kubernetes elkaars mTLS-handshakes kunnen verifiëren.

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

---

## 🔗 PKI-Integratie: Organisatie-brede Vertrouwensketen

De **PKI-Driven Service Mesh Demo** breidt de Zero-Trust Architectuur uit van een enkel cluster naar een organisatie-brede beveiligingsstrategie:

### Waarom is een PKI essentieel voor Zero-Trust?

* **Centrale Source of Truth:** Zonder PKI genereert elk Istio-cluster zijn eigen, onafhankelijke cryptografische Root. Dit werkt intern, maar voorkomt vertrouwen tussen clusters of met externe systemen. Een PKI fungeert als de centrale autoriteit die bepaalt wie er binnen de organisatie vertrouwd mag worden.

* **Gekoppeld Vertrouwen (Chain of Trust):** Door Istio te koppelen aan een overkoepelende PKI, worden alle workload-certificaten herleidbaar naar één organisatie-autoriteit. Hierdoor kunnen systemen binnen én buiten Kubernetes elkaars mTLS-handshakes verifiëren - essentieel voor hybride en multi-cloud omgevingen.

* **Risicobeheersing (Sleutelbeheer):** De absolute Root-sleutel van de organisatie blijft offline of in een hardwaremodule (HSM). Kubernetes krijgt via de PKI slechts een *Intermediate CA-certificaat* toegewezen. Mocht een cluster gecompromitteerd raken, dan hoeft alleen dit specifieke tussenstation te worden ingetrokken, zonder de rest van de infrastructuur te raken.

### De Uitgebreide Zero-Trust Keten

Met PKI-integratie wordt de vertrouwensketen volledig:

```text
[ Organisatie PKI (Root CA) ]
          │
  (Intermediate CA-certificaat)
          ▼
   [ Istio Control Plane ]
          │
  (Genereert workload-certificaten)
          ▼
   [ Envoy Proxy A ] <─── Verifieert: Certificaat herleidbaar naar organisatie-PKI?
          │
    (Strict mTLS Tunnel)
          ▼
   [ Envoy Proxy B ]
          │
          ▼
    [ Backend App ]  <─── Verifieert: JWT-token + Workload Identity
```

### Zero-Trust Principes in Praktijk

- **Never Trust, Always Verify:** Elk certificaat wordt continu geverifieerd tegen de organisatie-PKI, niet alleen tegen een lokale cluster-CA.
- **Least Privilege:** Intermediate CA's beperken de impact van een compromissie tot één domein of cluster.
- **Assume Breach:** Mocht een cluster worden gecompromitteerd, kan de organisatie de Intermediate CA intrekken zonder de Root CA te hoeven herroepen.
