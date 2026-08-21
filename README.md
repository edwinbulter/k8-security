# Security Platform: Zero-Trust Architectuur PoC

Dit project bevat zes onafhankelijke Proof of Concepts (PoCs) die samen de basis vormen voor een gelaagde beveiligingsarchitectuur binnen Kubernetes.

---

## 📂 Beschikbare Demo's

Dit project is opgedeeld in zes lagen. Elke demo heeft een eigen map met specifieke configuratie en een uitgebreide handleiding in de bijbehorende `README.md`.

1. 👤 **[User Identity & SSO Demo](./keycloak-sso/README.md)**
    Authenticatie en Single Sign-On (SSO) voor menselijke gebruikers via het BFF-patroon met Keycloak en OAuth2-Proxy.
    *Zie de [Demo Handleiding](./keycloak-sso/README.md) voor setup instructies.*

2. 🤖 **[Workload Identity & Service Mesh Demo](./service-mesh/README.md)**
    Identificatie en versleuteling van machine-naar-machine communicatie met Istio en Strict mTLS.
    *Zie de [Demo Handleiding](./service-mesh/README.md) voor setup instructies.*

3. 🔐 **[PKI-Driven Service Mesh Demo](./pki-service-mesh/README.md)**
    Organisatie-brede vertrouwensketen door Istio te koppelen aan een centrale PKI via cert-manager.
    *Zie de [Demo Handleiding](./pki-service-mesh/README.md) voor setup instructies.*

4. 🗄️ **[Keycloak User Storage Provider Demo](./keycloak-userprovider/README.md)**
    Federatie van identiteiten door Keycloak live te koppelen aan een externe PostgreSQL gebruikersdatabase via JPA.
    *Zie de [Demo Handleiding](./keycloak-userprovider/README.md) voor setup instructies.*

5. 🔑 **[Keycloak User Permissions SPI Demo](./keycloak-user-permissions-spi/README.md)**
    Custom Protocol Mapper die permissions uit een externe PostgreSQL database als JWT claims injecteert, met twee Node.js web clients en SSO.
    *Zie de [Demo Handleiding](./keycloak-user-permissions-spi/README.md) voor setup instructies.*

6. 🏛️ **[midPoint + Keycloak Identity Governance Demo](./midpoint-keycloak/README.md)**
    Evolveum midPoint als Identity Governance & Administration platform dat gebruikers en role assignments provisioneert naar PostgreSQL, geïntegreerd met Keycloak voor authenticatie en JWT permissies.
    *Zie de [Demo Handleiding](./midpoint-keycloak/README.md) voor setup instructies.*

---

## 🔐 De Samenwerking: Een Zero-Trust Keten

De combinatie van deze demo's zorgt voor een doorlopende verificatie op zowel gebruikersniveau, identiteitsbronnen als netwerkniveau:

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
* **Zonder User Storage Provider:** Kan Keycloak alleen gebruikers authenticeren die in de eigen database zijn aangemaakt. Bestaande gebruikersdatabases (bijv. HR-systemen, legacy directories) moeten handmatig worden gemigreerd, wat leidt tot identiteitsversnippering en synchronisatieproblemen.

**Samen** zorgen ze ervoor dat elk verzoek op elk niveau wordt gecontroleerd en dat identiteiten vanuit bestaande bronnen naadloos worden geïntegreerd. Dit sluit aan bij het uitgangspunt: *Never trust, always verify.*

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

---

## 🔗 Federated Identity: Integratie met Bestaande Identiteitsbronnen

De **Keycloak User Storage Provider Demo** vult de Zero-Trust architectuur aan op het niveau van identiteitsbeheer:

### Waarom is Federated Identity essentieel voor Zero-Trust?

* **Single Source of Truth:** Organisaties hebben vaak al een autoritatieve gebruikersdatabase (bijv. een HR-systeem of legacy directory). Zonder federatie moeten gebruikers handmatig worden aangemaakt in Keycloak, wat leidt tot inconsistenties en dubbel beheer.

* **Live Integratie:** De User Storage Provider haalt gebruikers live uit de externe PostgreSQL database via JPA/Hibernate. Wijzigingen in de brondatabase zijn direct zichtbaar in Keycloak — geen synchronisatie of migratie nodig.

* **Gedeelde Verantwoordelijkheid:** Keycloak blijft verantwoordelijk voor authenticatie en sessiebeheer, terwijl de externe database de autoriteit blijft over gebruikersgegevens. Dit past bij het Zero-Trust principe waarbij elke component zijn eigen verantwoordelijkheid behoudt.

### De Federated Identity Keten

```text
[ Externe PostgreSQL Database ]
          │
  (JPA User Storage Provider)
          ▼
   [ Keycloak ]  <─── Haalt gebruikers live op bij inloggen of zoekopdracht
          │
  (Authenticatie met wachtwoord uit externe DB)
          ▼
   [ OAuth2-Proxy / Client App ]  <─── Ontvangt JWT-token met federated identiteit
```

### Federated Identity Principes in Praktijk

- **Never Trust, Always Verify:** Keycloak vertrouwt niet op een gekopieerde gebruikerslijst, maar haalt gebruikersgegevens live uit de autoritatieve bron.
- **Least Privilege:** De provider heeft alleen-lezen toegang tot de gebruikersdatabase; wijzigingen gaan via de normale Kanalen van de bronorganisatie.
- **Assume Breach:** Als Keycloak wordt gecompromitteerd, blijven de wachtwoorden veilig in de externe database — de aanvaller krijgt geen toegang tot de brondata.

---

## 🏛️ Identity Governance: midPoint + Keycloak Integratie

De **midPoint + Keycloak Demo** voegt Identity Governance & Administration (IGA) toe aan de Zero-Trust architectuur:

### Waarom is IGA essentieel voor Zero-Trust?

* **Governed Lifecycle:** Zonder IGA worden gebruikers handmatig aangemaakt en verwijderd, wat leidt tot "orphan accounts" en onterechte toegang. midPoint automatiseert de volledige gebruikerslicycle: onboarding, role changes, offboarding.

* **Source of Truth:** midPoint fungeert als centrale autoriteit voor "wie heeft welke rol". Wijzigingen in midPoint worden automatisch geprovisioneerd naar de PostgreSQL database die Keycloak leest. Dit elimineert handmatige SQL manipulatie.

* **Audit & Compliance:** midPoint houdt een volledige audit trail bij van alle wijzigingen — wie heeft welke rol toegewezen en wanneer. Dit is essentieel voor compliance (GDPR, ISO 27001, SOC 2).

### De IGA Keten

```text
[ midPoint (IGA) ]
   - User lifecycle management
   - Role assignment & governance
          │
   (Provisioning via JDBC)
          ▼
   [ PostgreSQL (userdb) ]
   - users, user_roles tabellen
          │
   (JPA User Storage Provider)
          ▼
   [ Keycloak ]  <─── Leest gebruikers en permissies live
          │
   (OIDC + Permission Protocol Mapper)
          ▼
   [ Web Clients ]  <─── JWT tokens met governed permissies
```

### IGA Principes in Praktijk

- **Never Trust, Always Verify:** Keycloak leest permissies live uit de database die door midPoint wordt beheerd — geen vertrouwen op verouderde caches.
- **Least Privilege:** midPoint zorgt dat gebruikers alleen de rollen hebben die ze nodig hebben; verwijderde rollen verdwijnen direct uit het JWT token bij volgende login.
- **Assume Breach:** Bij compromittering van Keycloak blijft midPoint de autoriteit — de aanvaller kan geen rollen toewijzen of gebruikers aanmaken.
