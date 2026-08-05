# PKI-Driven Service Mesh Demo

Dit project is een uitbreiding op de [service-mesh demo](/service-mesh). Waar de eerdere demo zich richtte op het afdwingen van mTLS tussen applicaties via de standaard, ingebouwde clusterbeveiliging van Istio (`istio-ca-secret`), voegt dit project een koppeling met een **lokale PKI (Public Key Infrastructure)** toe.

---

## 📖 Snel aan de slag

De stapsgewijze installatiehandleiding voor het inrichten en cryptografisch valideren van deze opzet binnen een enkele sandbox-namespace is te vinden in:

👉 **[Bekijk de PKI Installatie- & Validatiehandleiding](doc/01-setup-pki-service-mesh.md)**

---

## 🧠 Wat is de toegevoegde waarde van de PKI?

In een stand-alone Kubernetes-cluster genereert Istio standaard zijn eigen, onafhankelijke cryptografische Root. Dit werkt voor intern pod-verkeer, maar is ontoegankelijk zodra het cluster moet communiceren met systemen *buiten* Kubernetes (zoals databases of applicaties in andere clusters), omdat zij die specifieke interne Istio-sleutel niet kennen of vertrouwen.

De toevoeging van een PKI (`cert-manager`) lost dit op:
* **Centrale Source of Truth:** De PKI fungeert als de centrale autoriteit die bepaalt wie er binnen de organisatie vertrouwd mag worden.
* **Gekoppeld Vertrouwen (Chain of Trust):** Istio wordt verplicht om een certificaat te gebruiken dat herleidbaar is naar de overkoepelende autoriteit van de organisatie. Hierdoor kunnen systemen binnen én buiten Kubernetes elkaars mTLS-handshakes verifiëren.
* **Risicobeheersing (Sleutelbeheer):** De absolute Root-sleutel van de organisatie blijft offline of in een hardwaremodule (HSM). Kubernetes krijgt via de PKI slechts een *Intermediate CA-certificaat* toegewezen. Mocht het cluster gecompromitteerd raken, dan hoeft alleen dit specifieke tussenstation te worden ingetrokken, zonder de rest van de infrastructuur te raken.

---

## ⚙️ Hoe maakt de Service Mesh hier gebruik van?

Het Control Plane van Istio (`istiod`) scant bij het opstarten de netwerkomgeving:

1. **De PKI Genereert de Sleutels:** Binnen de demo-namespace (`pki-demo`) maakt `cert-manager` de cryptografische CA-sleutels aan.
2. **De Koppeling via `cacerts`:** Deze sleutels worden gekopieerd naar de beheerlaag van Istio onder de specifieke naam **`cacerts`**.
3. **De Schakeling:** Zodra Istio dit `cacerts` secret detecteert, stopt het met het gebruiken van de eigen, ingebouwde certificaten (`istio-ca-secret`).
4. **Workload Certificering:** Vanaf dat moment gebruikt Istio de private key uit het `cacerts` secret om alle kortlevende mTLS-certificaten voor de Envoy-sidecars (`sleep` en `httpbin`) te ondertekenen.

---

## 🛠️ Projectstructuur

```text
.
├── README.md                          # Dit bestand (concepten & overzicht)
└── doc/
    └── 01-setup-pki-service-mesh.md   # Handleiding met de stappen en openssl-verificatie
```
