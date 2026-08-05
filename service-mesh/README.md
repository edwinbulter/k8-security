# Service Mesh mTLS Demo

Dit project bevat een minimale, cloud-native Proof of Concept (PoC) waarin wordt gedemonstreerd hoe een Service Mesh (Istio) op platformniveau **Strict mTLS** afdwingt tussen applicaties, zonder dat development teams hiervoor ingewikkelde beveiligingscode of certificaatbeheer hoeven in te richten.

---

## 📖 Snel aan de slag

De volledige, stapsgewijze installatiehandleiding voor het opzetten van deze demo in een lokaal (KinD) cluster vind je in de documentatiemap:

👉 **[Bekijk de Installatie-instructies](doc/01-setup-service-mesh.md)**

---

## 🧠 Hoe het werkt (Architectuur & Netwerkflow)

Binnen deze demo bootsen we een Zero Trust netwerkomgeving na. Het mechanisme werkt volledig geautomatiseerd op de achtergrond via de volgende principes:

* **Dynamic Sidecar Injection:** In de demo zorgt Istio (de service mesh) ervoor dat elke pod binnen de namespace een `Envoy Proxy` sidecar container (een initContainer met `restartPolicy="Always"`) krijgt geïnjecteerd omdat de namespace is gelabeled met `istio-injection=enabled`. Hierdoor hoeven ontwikkelaars de proxy niet handmatig in hun eigen deployment-YAML op te nemen.
* **Verkeersonderschepping:** Als `Pod A` (bijvoorbeeld de `sleep` cliënt) `Pod B` (de `httpbin` backend) aanroept via een regulier HTTP-verzoek, dan kaapt `Envoy Proxy A` het uitgaande verkeer van `Pod A` direct weg op netwerkniveau (middels `iptables`).
* **De Handshake:** Proxy A opent een TLS-verbinding met de Envoy-proxy van de backend (Proxy B). Proxy B laat zijn certificaat zien aan Proxy A. Proxy A controleert dit tegen zijn *truststore* (het Istio CA-certificaat). Omdat het *mutual* (wederzijdse) TLS is, vraagt Proxy B ook om het certificaat van Proxy A. Proxy B valideert dit op zijn beurt tegen zijn eigen *truststore*.
* **De Versleutelde Tunnel:** Als beide certificaten geldig zijn, wordt er een encrypted tunnel opgezet. De data wordt veilig over het netwerk getransporteerd. Proxy B ontsleutelt het verkeer pas binnen de pod-grens en levert het veilig af bij de backend-applicatie.

---

## 🛠️ Projectstructuur

```text
.
├── README.md                     # Dit bestand (architectuur & overzicht)
└── doc/
    └── 01-setup-service-mesh.md  # Stapsgewijze handleiding (CLI commando's & validatietests)
```
