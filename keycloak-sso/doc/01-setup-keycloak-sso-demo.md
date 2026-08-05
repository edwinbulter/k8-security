# Keycloak SSO Test op een lokaal Kind-cluster

Deze handleiding beschrijft hoe je een Single Sign-On (SSO) test opzet binnen je lokale `kind-multi-node-cluster`. We gebruiken Keycloak als Identity Provider (IdP) en twee aparte `oauth2-proxy` instanties die fungeren als beveiligde web-apps. 

Wanneer je inlogt op App 1, ben je via SSO automatisch ook ingelogd op App 2.

## 🛠️ Voorbereiding: DNS & Context instellen

### 1. Koppel je kubectl aan de juiste context
```bash
kubectl config use-context kind-multi-node-cluster
```

### 2. Pas je lokale `/etc/hosts` bestand aan
Keycloak deelt de sessie via een browser-cookie. Om te zorgen dat de browser dit cookie voor beide apps accepteert, moeten ze op hetzelfde hoofddomein draaien. Voeg de volgende regel toe aan `/etc/hosts`:

```text
127.0.0.1   keycloak.local app1.local app2.local
```

### 3. Ingress Controller activeren
Zorg ervoor dat de Ingress controller actief is op je Kind-cluster (indien nog niet gedaan):
```bash
kubectl apply -f https://githubusercontent.com
```

---

## 📦 Stap 1: Namespace & Keycloak Uitrollen

Maak een bestand genaamd `01-keycloak.yaml` aan:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: keycloak-sso-demo
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
  namespace: keycloak-sso-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: keycloak
  template:
    metadata:
      labels:
        app: keycloak
    spec:
      containers:
      - name: keycloak
        image: quay.io/keycloak/keycloak:24.0.0
        args: ["start-dev"]
        env:
        - name: KEYCLOAK_ADMIN
          value: "admin"
        - name: KEYCLOAK_ADMIN_PASSWORD
          value: "adminpassword"
        - name: KC_PROXY
          value: "edge"
        - name: KC_HOSTNAME_STRICT
          value: "false"
        ports:
        - containerPort: 8080
          name: http
---
apiVersion: v1
kind: Service
metadata:
  name: keycloak
  namespace: keycloak-sso-demo
spec:
  ports:
  - port: 8080
    targetPort: 8080
  selector:
    app: keycloak
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: keycloak-ingress
  namespace: keycloak-sso-demo
spec:
  rules:
  - host: keycloak.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: keycloak
            port:
              number: 8080
```

**Uitvoeren:**
```bash
kubectl apply -f 01-keycloak.yaml
```

---

## ⚙️ Stap 2: Keycloak Configureren (Handmatig)

1. Ga in je browser naar `http://keycloak.local` en log in met `admin` / `adminpassword`.
2. Klik linksboven op **Master** -> **Create Realm**. Noem de nieuwe realm: `DemoRealm`.
3. Ga binnen `DemoRealm` naar **Users** -> **Add user**. Maak een user aan genaamd `testgebruiker`. Ga daarna naar het tabblad **Credentials**, stel een wachtwoord in (bijv. `welkom01`) en zet *Temporary* **UIT**.
4. Ga naar **Clients** -> **Create client** en maak twee clients aan:

   **Client 1 (Voor App 1):**
   * **Client ID:** `oauth2-proxy-app1`
   * **Client Protocol:** `openid-connect`
   * Klik *Next*, zet **Client Authentication** aan (Confidential flow).
   * **Valid Redirect URIs:** `http://app1.local/oauth2/callback`
   * **Web Origins:** `http://app1.local`
   * Klik *Save*. Ga naar het tabblad **Credentials** en kopieer het **Client Secret**.

   **Client 2 (Voor App 2):**
   * **Client ID:** `oauth2-proxy-app2`
   * **Client Protocol:** `openid-connect`
   * Klik *Next*, zet **Client Authentication** aan (Confidential flow).
   * **Valid Redirect URIs:** `http://app2.local/oauth2/callback`
   * **Web Origins:** `http://app2.local`
   * Klik *Save*. Ga naar het tabblad **Credentials** en kopieer het **Client Secret**.

---

## 🌐 Stap 3: De Webapps (oauth2-proxy) Uitrollen

Maak een bestand genaamd `02-webapps.yaml` aan. **Vervang** `<CLIENT_SECRET_APP1>` en `<CLIENT_SECRET_APP2>` door de secrets die je zojuist uit Keycloak hebt gekopieerd.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app1
  namespace: keycloak-sso-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: app1
  template:
    metadata:
      labels:
        app: app1
    spec:
      containers:
      - name: oauth2-proxy
        image: quay.io/oauth2-proxy/oauth2-proxy:v7.6.0
        args:
        - --provider=oidc
        - --client-id=oauth2-proxy-app1
        - --client-secret=<CLIENT_SECRET_APP1>
        - --skip-oidc-discovery=false
        - --insecure-oidc-skip-issuer-verification=true
        - --prompt=select_account
        - --skip-provider-button=true
        - --set-xauthrequest=true
        - --oidc-issuer-url=http://keycloak.keycloak-sso-demo.svc.cluster.local:8080/realms/DemoRealm
        - --upstream=static://200
        - --proxy-websockets=false
        - --http-address=0.0.0.0:4180
        - --redirect-url=http://app1.local/oauth2/callback
        - --cookie-name=_oauth2_proxy_app1
        - --cookie-secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=
        - --cookie-domain=app1.local
        - --cookie-secure=false
        - --email-domain=*      
        ports:
        - containerPort: 4180
          name: http
---
apiVersion: v1
kind: Service
metadata:
  name: app1
  namespace: keycloak-sso-demo
spec:
  ports:
  - port: 4180
    targetPort: 4180
  selector:
    app: app1
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app2
  namespace: keycloak-sso-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: app2
  template:
    metadata:
      labels:
        app: app2
    spec:
      containers:
      - name: oauth2-proxy
        image: quay.io/oauth2-proxy/oauth2-proxy:v7.6.0
        args:
        - --provider=oidc
        - --client-id=oauth2-proxy-app2
        - --client-secret=<CLIENT_SECRET_APP2>
        - --skip-oidc-discovery=false
        - --insecure-oidc-skip-issuer-verification=true
        - --prompt=select_account
        - --skip-provider-button=true
        - --set-xauthrequest=true
        - --oidc-issuer-url=http://keycloak.keycloak-sso-demo.svc.cluster.local:8080/realms/DemoRealm
        - --upstream=static://200
        - --proxy-websockets=false
        - --http-address=0.0.0.0:4180
        - --redirect-url=http://app2.local/oauth2/callback
        - --cookie-name=_oauth2_proxy_app2
        - --cookie-secret=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=
        - --cookie-domain=app2.local
        - --cookie-secure=false
        - --email-domain=*
        ports:
        - containerPort: 4180
          name: http
---
apiVersion: v1
kind: Service
metadata:
  name: app2
  namespace: keycloak-sso-demo
spec:
  ports:
  - port: 4180
    targetPort: 4180
  selector:
    app: app2
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: webapps-ingress
  namespace: keycloak-sso-demo
  annotations:
     # CRUCIAAL: Verhoog de buffers om de grote Keycloak-cookies te kunnen verwerken
     nginx.ingress.kubernetes.io/proxy-buffer-size: "16k"
     nginx.ingress.kubernetes.io/proxy-buffers: "4 16k"
     nginx.ingress.kubernetes.io/proxy-busy-buffers-size: "24k"
     nginx.ingress.kubernetes.io/proxy-header-buffer-size: "16k"
spec:
  rules:
  - host: app1.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: app1
            port:
              number: 4180
  - host: app2.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: app2
            port:
              number: 4180
```

**Uitvoeren:**
```bash
kubectl apply -f 02-webapps.yaml
```

---

## 🧪 Stap 4: De SSO Werking Testen

1. Open een **Incognito-venster** in je browser.
2. Navigeer naar `http://app1.local`.
3. `oauth2-proxy` herkent dat je niet geauthenticeerd bent en stuurt je door naar het inlogscherm van `keycloak.local`.
4. Log in met `testgebruiker` en het door jou gekozen wachtwoord.
5. Na succesvol inloggen word je teruggestuurd naar `http://app1.local`. Je ziet nu de standaard succes-pagina van `oauth2-proxy`.
6. Open nu binnen **hetzelfde incognito-venster** een nieuw tabblad en ga naar `http://app2.local`.
7. **De SSO-Magie:** Omdat beide apps het cookie-domein `.local` delen, herkent Keycloak je actieve sessie. Je wordt **direct ingelogd** op App 2 zonder opnieuw je wachtwoord te hoeven typen!
