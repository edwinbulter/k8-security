# Platform Engineering: Single-Namespace mTLS Demo (Tijdelijk)

Deze demo simuleert een veilige platformomgeving binnen een **bestaand** Kubernetes cluster met behulp van de `mesh-demo` namespace. Na het testen kun je deze namespace in zijn geheel verwijderen, waardoor het cluster direct weer helemaal schoon is. Hiermee demonstreren we hoe je op platformniveau **Strict mTLS** afdwingt tussen applicaties zonder dat development teams security-code hoeven te schrijven.

## Vereisten
- Een draaiend (KinD) Kubernetes cluster.
- `kubectl` CLI-tool.

### Istio CLI (istioctl) installeren via Homebrew
Als je op een Mac werkt, installeer je `istioctl` het snelst en makkelijkst via Homebrew. Dit zorgt ervoor dat het commando direct overal op je systeem werkt:

```bash
# Installeer istioctl via Homebrew
brew install istioctl

# Controleer of de installatie is geslaagd en overal werkt
istioctl version
```

---

## Stap 1: Installeer Istio op het cluster
We installeren het **Service Mesh Control Plane** (`istiod`). We gebruiken hier bewust het lichte `--set profile=demo` profiel. Dit profiel is geoptimaliseerd voor lokale testomgevingen (zoals KinD op een laptop) en verbruikt slechts **100 MB tot 300 MB RAM**, waardoor de impact op je systeem minimaal is.

```bash
istioctl install --set profile=demo -y
```

## Stap 2: Maak de tijdelijke Mesh Demo Namespace aan
We maken een geïsoleerde namespace aan en vertellen Istio dat er in deze specifieke namespace sidecar-proxies geïnjecteerd moeten worden.

```bash
kubectl create namespace mesh-demo
kubectl label namespace mesh-demo istio-injection=enabled
```

## Stap 3: Deploy de Test-Applicaties
We starten twee microservices binnen de `mesh-demo` namespace:
1. `sleep` (App-A: De cliënt die data opvraagt)
2. `httpbin` (App-B: De backend webserver/API)

De yaml files komen van https://github.com/istio/istio/tree/master/samples

```bash
# Start de backend webserver (App-B)
kubectl apply -f httpbin.yaml -n mesh-demo

# Start de cliënt container (App-A)
kubectl apply -f sleep.yaml -n mesh-demo
```

*Controleer of de pods up-and-running zijn: `kubectl get pods -n mesh-demo`. Je moet `2/2` containers zien draaien per pod.*

## Stap 4: Dwing STRICT mTLS af in de Mesh Demo
We dwingen nu via een `PeerAuthentication` resource **STRICT mTLS** af. Omdat we de namespace expliciet definiëren, heeft dit *geen* invloed op de rest van het cluster.

Maak een bestand genaamd `mesh-demo-mtls-strict.yaml`:
```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: mesh-demo-strict-mtls
  namespace: mesh-demo
spec:
  mtls:
    mode: STRICT
```

Pas dit toe op het cluster:
```bash
kubectl apply -f mesh-demo-mtls-strict.yaml
```

---

## Uitleg stap 1 t/m 4

### 💡 Waarom zie je de Envoy-container niet terug in de `httpbin.yaml`?

Als je de broncode van `httpbin.yaml` bekijkt, zie je onder de sectie `containers:` maar **één enkele container** staan: de `go-httpbin` applicatie zelf. Toch zie je na de deploy met `kubectl get pods` dat er `2/2` containers actief zijn.

Dit is de absolute kern van **Platform Engineering**: de developer hoeft de proxy niet zelf in zijn code of YAML-bestanden te zetten. Het platform regelt dit volledig automatisch op de achtergrond via **Dynamic Sidecar Injection**.

#### Het mechanisme: Mutating Admission Webhooks

Wanneer jij het commando `kubectl apply -f httpbin.yaml` uitvoert, gebeurt er achter de schermen het volgende binnen de Kubernetes API-server:

1. **De Aanvraag:** De API-server ontvangt jouw YAML-bestand met daarin slechts één container.
2. **De Controle (Webhook):** Voordat Kubernetes de pod daadwerkelijk aanmaakt, kijkt een speciaal controlemechanisme (de *Mutating Admission Webhook* van Istio) naar de bestemming.
3. **Het Label Scannen:** De webhook ziet dat de pod wordt geplaatst in de namespace `mesh-demo`, welke we in Stap 2 hebben voorzien van het label `istio-injection=enabled`.
4. **De YAML Aanpassen (Mutation):** Het Control Plane (`istiod`) grijpt nu live in. Het *wijzigt* de configuratie in het geheugen van de API-server vlak voordat de pod start. Istio voegt automatisch de volgende componenten toe aan de pod-definitie:
   * **`istio-init` (InitContainer):** Een tijdelijke container die de netwerkregels (`iptables`) van de pod zo aanpast dat al het inkomende en uitgaande verkeer verplicht wordt omgeleid naar de proxy.
   * **`istio-proxy` (Sidecar):** De daadwerkelijke **Envoy-proxy** container die verantwoordelijk is voor de mTLS-handshake en certificaatvalidatie.
5. **De Start:** Kubernetes start de pod met de aangepaste configuratie. De lokale `httpbin.yaml` op jouw harde schijf blijft ongewijzigd, maar in het cluster draaien er nu tóch twee containers.

#### Het bewijs bekijken in het cluster

Je kunt de door Istio aangepaste live-configuratie direct zelf inspecteren in je cluster met het volgende commando:

```bash
kubectl get pod -l app=httpbin -n mesh-demo -o yaml
```

Als je door de output heen scrolt, zul je onder de sectie `containers:` nu wel degelijk de automatische toevoeging van de `istio-proxy` (Envoy) container zien staan, inclusief alle bijbehorende omgevingsvariabelen en security-instellingen die door het platform zijn geïnjecteerd.


---
## Stap 5: Het Bewijs (Testen & Valideren)

### Test 1: Succesvolle mTLS (Binnen de Mesh Demo)
We sturen een verzoek van App-A naar App-B binnen de beveiligde namespace. De Envoy-proxies handelen automatisch de mTLS-handshake af.

```bash
kubectl exec "\$(kubectl get pod -l app=sleep -n mesh-demo -o jsonpath={.items.metadata.name})" -c sleep -n mesh-demo -- curl -sI httpbin:8000/headers
```
**Verwacht resultaat:** Een succesvolle `HTTP/1.1 200 OK`.

### Test 2: Geweigerde verbinding (Vanuit een andere namespace)
We simuleren een onbeveiligde applicatie of indringer van buiten de mesh. We starten een tijdelijke pod in de standaard `default` namespace (waar Istio niet actief is). Dit verkeer mist een certificaat en moet worden geblokkeerd.

```bash
kubectl run unsecure-client --image=radial/busyboxplus:curl -n default -i --tty --rm -- curl -sI httpbin.mesh-demo.svc.cluster.local:8000/headers
```
**Verwacht resultaat:** De verbinding wordt direct geweigerd (`command terminated with exit code 56` of een netwerk timeout). De backend weigert het verkeer omdat de client geen mTLS-certificaat kan overleggen.

---

### 💡 Hoe de mTLS Communicatie Flow verloopt (Test 1)
Wanneer applicatie `sleep` (App-A) een verzoek stuurt naar `httpbin` (App-B), verloopt de mTLS-handshake volledig op de netwerklaag tussen de twee Envoy-proxies. De applicaties zelf merken hier niks van:

```text
[ sleep-app ] --( Onbeveiligd HTTP )--> [ Envoy Proxy A ]
                                               |
                                     ( STRICT mTLS Handshake )
                                               |
[ httpbin-app ] <--( Onbeveiligd HTTP )-- [ Envoy Proxy B ]
```

1. **De Aanroep:** De applicatie-container `sleep` stuurt een regulier, onbeveiligd HTTP-verzoek naar `httpbin:8000`.
2. **De Onderschepping:** De lokale Envoy-proxy (Proxy A) kaapt dit uitgaande verkeer direct weg via netwerkregels (`iptables`) binnen de pod.
3. **De Handshake:** Proxy A opent een TLS-verbinding met de Envoy-proxy van de backend (Proxy B). Proxy B laat zijn certificaat zien aan Proxy A. Proxy A controleert dit tegen zijn *truststore* (het Istio CA-certificaat). Omdat het *mutual* (wederzijdse) TLS is, vraagt Proxy B ook om het certificaat van Proxy A. Proxy B valideert dit op zijn beurt tegen zijn eigen *truststore*.
4. **De Versleutelde Tunnel:** Als beide certificaten geldig zijn, wordt er een encrypted tunnel opgezet. De data wordt veilig over het netwerk getransporteerd.
5. **De Aflevering:** Proxy B ontvangt de versleutelde data, ontsleutelt deze en stuurt het verzoek als gewone HTTP door naar de lokale `httpbin` applicatiecontainer.

---

### 💡 Waarom faalt de verbinding van buiten de Mesh? (Test 2)
In deze test is er **geen Envoy-sidecar proxy** aanwezig in de pod van de client.

* De `unsecure-client` stuurt een gewone HTTP-aanroep naar de backend in de `mesh-demo` namespace.
* De Envoy-proxy van `httpbin` (Proxy B) vangt dit inkomende verkeer op.
* Omdat we via `PeerAuthentication` de modus op **`STRICT`** hebben gezet, eist Proxy B direct een mTLS-handshake en een geldig X.509-certificaat van de zender.
* De `unsecure-client` heeft geen proxy en kan geen certificaat overleggen.
* Proxy B verbreekt onmiddellijk de verbinding op TCP-niveau. De indringer wordt effectief geweerd, en de `httpbin` applicatie krijgt het verzoek niet eens te zien.

---

### 💡 Hoe zorgt de Service Mesh voor Certificaten en Truststores?
In plaats van handmatig `.jks` of `.p12` truststores und keystores te genereren met OpenSSL, automatiseert Istio dit volledig via het geheugen:

* **De Certificate Authority (CA):** Het Control Plane van Istio (`istiod`) functioneert als de centrale, vertrouwde Root CA binnen je cluster.
* **Automatische Uitgifte (De Keystore):** Zodra een pod opstart in een gelabelde namespace, ziet `istiod` dit. `istiod` genereert realtime een X.509-certificaat en een private key specifiek voor die pod. De identiteit in het certificaat wordt gekoppeld aan het Kubernetes `ServiceAccount` (gebaseerd op de open **SPIFFE**-standaard).
* **Geen bestanden op schijf:** De Envoy-sidecar proxy in de pod haalt dit certificaat via een beveiligd intern kanaal (Secret Discovery Service) rechtstreeks op uit het geheugen van `istiod`. Het certificaat wordt *nooit* op de harde schijf van de container opgeslagen.
* **De Truststore:** `istiod` pusht ook direct zijn eigen Root CA-certificaat naar de Envoy-proxy. Dit fungeert als de *truststore*. Envoy gebruikt deze om te controleren of certificaten van andere pods door dezelfde betrouwbare Istio CA zijn ondertekend.
* **Automatische Rotatie:** De certificaten hebben een zeer korte levensduur (vaak slechts enkele uren). `istiod` vernieuwt ze automatisch op de achtergrond zonder dat de applicatie herstart hoeft te worden.

---

### 💡 Waarom zie je de Envoy-container niet terug in de `httpbin.yaml`?

Als je de broncode van `httpbin.yaml` bekijkt, zie je onder de sectie `containers:` maar **één enkele container** staan: de `go-httpbin` applicatie zelf. Toch zie je na de deploy met `kubectl get pods` dat er `2/2` containers actief zijn.

Dit is de absolute kern van **Platform Engineering**: de developer hoeft de proxy niet zelf in zijn code of YAML-bestanden te zetten. Het platform regelt dit volledig automatisch op de achtergrond via **Dynamic Sidecar Injection**.

#### Het mechanisme: Mutating Admission Webhooks

Wanneer jij het commando `kubectl apply -f httpbin.yaml` uitvoert, gebeurt er achter de schermen het volgende binnen de Kubernetes API-server:

1. **De Aanvraag:** De API-server ontvangt jouw YAML-bestand met daarin slechts één container.
2. **De Controle (Webhook):** Voordat Kubernetes de pod daadwerkelijk aanmaakt, kijkt een speciaal controlemechanisme (de *Mutating Admission Webhook* van Istio) naar de bestemming.
3. **Het Label Scannen:** De webhook ziet dat de pod wordt geplaatst in de namespace `mesh-demo`, welke we in Stap 2 hebben voorzien van het label `istio-injection=enabled`.
4. **De YAML Aanpassen (Mutation):** Het Control Plane (`istiod`) grijpt nu live in. Het *wijzigt* de configuratie in het geheugen van de API-server vlak voordat de pod start. Istio voegt automatisch de volgende componenten toe aan de pod-definitie:
    * **`istio-init` (InitContainer):** Een tijdelijke container die de netwerkregels (`iptables`) van de pod zo aanpast dat al het inkomende en uitgaande verkeer verplicht wordt omgeleid naar de proxy.
    * **`istio-proxy` (Sidecar):** De daadwerkelijke **Envoy-proxy** container die verantwoordelijk is voor de mTLS-handshake en certificaatvalidatie.
5. **De Start:** Kubernetes start de pod met de aangepaste configuratie. De lokale `httpbin.yaml` op jouw harde schijf blijft ongewijzigd, maar in het cluster draaien er nu tóch twee containers.

#### Het bewijs bekijken in het cluster

Je kunt de door Istio aangepaste live-configuratie direct zelf inspecteren in je cluster met het volgende commando:

```bash
kubectl get pod -l app=httpbin -n mesh-demo -o yaml
```

Als je door de output heen scrolt, zul je onder de sectie `containers:` nu wel degelijk de automatische toevoeging van de `istio-proxy` (Envoy) container zien staan, inclusief alle bijbehorende omgevingsvariabelen en security-instellingen die door het platform zijn geïnjecteerd.

---

## Stap 6: Alles in één keer opruimen

### 1. De sandbox-namespace verwijderen
Ben je klaar met testen? Verwijder dan eerst de namespace. Kubernetes verwijdert hiermee automatisch de test-applicaties en de mTLS-beveiligingsregels.

```bash
kubectl delete namespace mesh-demo
```

### 2. Istio volledig deinstalleren (Optioneel)
Istio blijft na de installatie in het cluster actief. Wil je het cluster weer 100% clean hebben en alle gereserveerde resources (RAM/CPU) direct vrijgeven op je computer? Verwijder de Service Mesh dan volledig met dit commando:

```bash
istioctl uninstall --purge -y
```
