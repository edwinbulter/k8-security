# Platform Engineering: In-Namespace PKI & Istio mTLS Demo (Tijdelijk)

Deze demo simuleert de integratie tussen een Certificate Authority (PKI via `cert-manager`) en een Service Mesh (Istio) binnen één geïsoleerde namespace genaamd `pki-demo`. We genereren een lokale Root CA binnen deze sandbox, dwingen **Strict mTLS** af, en bewijzen cryptografisch dat de workloads certificaten gebruiken die rechtstreeks door deze specifieke PKI zijn uitgegeven.

## Vereisten
- Een draaiend (KinD) Kubernetes cluster.
- `kubectl` en `helm` CLI-tools geïnstalleerd.

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

## Stap 2: Installeer cert-manager (De PKI Engine)
Als `cert-manager` nog niet op je cluster draait, installeer dit dan via Helm. Dit component beheert de certificaat-orchestratie.

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true
```

## Stap 3: Maak de Sandbox Namespace & de Lokale PKI aan
We maken de namespace `pki-demo` aan, labelen deze voor Istio-sidecar-injectie, en configureren een lokale *Self-Signed* Root CA die specifiek binnen deze namespace leeft.

```bash
# 1. Namespace aanmaken en labelen voor Istio
kubectl create namespace pki-demo
kubectl label namespace pki-demo istio-injection=enabled

# 2. Maak een lokale certificaat-uitgever (Issuer) aan binnen de namespace
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: lokale-pki-issuer
  namespace: pki-demo
spec:
  selfSigned: {}
EOF

# 3. Genereer het unieke Root CA-certificaat (Jouw lokale PKI)
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: pki-demo-root-ca
  namespace: pki-demo
spec:
  isCA: true
  commonName: pki-demo-root-ca
  secretName: lokale-pki-ca-secret
  privateKey:
    algorithm: ECDSA
    size: 256
  issuerRef:
    name: lokale-pki-issuer
    kind: Issuer
EOF
```

## Stap 4: Koppel de Lokale PKI aan het Istio Control Plane

Istio zoekt in de beheer-namespace (`istio-system`) naar een specifiek secret genaamd `cacerts` om een aangepaste PKI te consumeren (als die niet gebruikt hij de ingebouwde certificaten uit de secret `istio-ca-secret`). Het `cacerts` secret is een Intermediate CA-certificaat, het `istio-ca-secret` een self-signed Root-certificaat. We kopiëren het zojuist door `cert-manager` gegenereerde geheim naar de netwerkbeheerlaag:

```bash
# Kopieer het gegenereerde PKI-secret naar de beheerlaag van Istio (en pas daarbij de name en namespace aan via sed s/oud/nieuw/g)
kubectl get secret lokale-pki-ca-secret -n pki-demo -o yaml | \
  sed 's/name: lokale-pki-ca-secret/name: cacerts/g' | \
  sed 's/namespace: pki-demo/namespace: istio-system/g' | \
  kubectl apply -f -

# Herstart het Istio Control Plane om de nieuwe PKI-sleutels te laden
kubectl rollout restart deployment istiod -n istio-system
```

---

### 💡 Waarom is deze stap nodig? (Architectonische Toelichting)

Standaard functioneert Istio als een gesloten systeem. Als je Istio installeert, genereert de component `istiod` direct zelf een *self-signed* Root CA-certificaat in zijn eigen geheugen. Om te zorgen dat Istio certificaten uitgeeft die herleidbaar zijn naar jouw specifieke PKI, moet je Istio vertellen waar die CA-sleutels staan. Istio heeft hard gecodeerd in zijn software staan dat hij bij het opstarten zoekt naar een Kubernetes Secret met de exacte naam **`cacerts`** in de namespace **`istio-system`**.

#### ⚠️ Demo versus Productie (De Nuance)

De manier waarop we dit in deze demo oplossen (het handmatig kopiëren en hernoemen van een geheim via een `sed`-script), is puur noodzakelijk vanwege de **geïsoleerde opzet van deze sandbox**:

* **De Demo-beperking:** In deze demo draait onze PKI (`cert-manager`) binnen de tijdelijke sandbox-namespace `pki-demo`, terwijl het Control Plane van Istio (`istiod`) in `istio-system` leeft. Kubernetes staat om veiligheidsredenen **niet** toe dat een applicatie direct een Secret uitleest uit een andere namespace. Het script is een technische workaround om deze namespace-grens in een lokaal testcluster te overbruggen.
* **De Productie-aanpak:** In een echte productieomgeving wil je dit handmatige werk en het dupliceren van geheimen absoluut vermijden (vanwege het verlopen van certificaten). Daar gebruik je een cluster-brede **`ClusterIssuer`** die het certificaat direct geautomatiseerd in `istio-system` aanmaakt en realtime vernieuwt, of je configureert Istio via **CSR-provisioning** waarbij certificaataanvragen van pods direct digitaal naar de centrale Enterprise PKI worden doorgestuurd zonder dat er root-sleutels in het cluster hoeven te liggen.


## Stap 5: Deploy de Test-Applicaties en activeer STRICT mTLS
We starten de applicaties binnen de beveiligde sandbox en dwingen Strict mTLS af op de netwerklaag.

*Let op: In de onderstaande commando's zijn spaties rondom de slashes geplaatst voor de leesbaarheid.*

```bash
# Start de applicaties (App-A en App-B)
kubectl apply -f httpbin.yaml -n pki-demo
kubectl apply -f sleep.yaml -n pki-demo

# Dwing Strict mTLS af voor deze namespace
cat <<EOF | kubectl apply -f -
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: pki-demo-strict-mtls
  namespace: pki-demo
spec:
  mtls:
    mode: STRICT
EOF
```
*Controleer met `kubectl get pods -n pki-demo` of beide pods op status `Running` staan en `2/2` containers bevatten.*

---

## Stap 6: Het Cryptografische Bewijs (Verificatie)

We gaan nu controleren of de certificaten die de Envoy-sidecars onderling gebruiken voor de mTLS-handshake écht zijn uitgegeven door onze lokale PKI (`pki-demo-root-ca`).

### 1. Haal de certificaatketen uit de actieve Pod
We gebruiken de Istio CLI om de actieve TLS-certificaten rechtstreeks uit het geheugen van de `sleep` proxy te trekken:

```bash
istioctl proxy-config secret $(kubectl get pod -l app=sleep -n pki-demo -o jsonpath={.items.metadata.name}) -n pki-demo

# Of zo:
isioctl proxy-config secret sleep-7bf44b8df8-j6p42 -n pki-demo
```

### 2. Controleer de Root CA identiteit
Om het onomstotelijke bewijs te leveren, inspecteren we het Root-certificaat dat de proxy momenteel gebruikt als zijn *truststore*. We filteren de output op de `Subject` (de naam van de uitgever):

```bash
kubectl exec $(kubectl get pod -l app=sleep -n pki-demo -o jsonpath={.items.metadata.name}) -c istio-proxy -n pki-demo -- openssl x509 -in /var/run/secrets/istio/root-cert.pem -text -noout | grep "Subject:"
```

**Verwacht resultaat:**
```text
Subject: CN = pki-demo-root-ca
```
*Als je hier `CN = pki-demo-root-ca` ziet staan, is het bewijs geleverd. De applicatie gebruikt niet langer het standaard Istio-certificaat, maar is succesvol gekoppeld aan de PKI die jij live in de namespace hebt opgetrokken.*

---

## Stap 7: mTLS testen

Je kunt nu de mTLS communicatie testen. Omdat dit al is gedaan in de `service-mesh` demo, kun je de uitleg en uitvoering daarvan bekijken in: [01-setup-service-mesh](./../../service-mesh/doc/01-setup-service-mesh.md#stap-5-het-bewijs-testen--valideren) 


## Stap 8: Alles in één keer opruimen

Wanneer je klaar bent met testen, kun je de tijdelijke resources met de volgende commando's volledig en netjes uit het cluster verwijderen, zodat alle CPU en RAM direct weer vrijkomen.

```bash
# 1. Verwijder de complete sandbox-namespace (wist de apps en de lokale PKI)
kubectl delete namespace pki-demo

# 2. Verwijder de gekoppelde certificaatsleutels uit de netwerkbeheerlaag
kubectl delete secret cacerts -n istio-system

# 3. Herstart Istio zodat deze (indien gewenst) terugvalt op zijn standaardgedrag
kubectl rollout restart deployment istiod -n istio-system
```
