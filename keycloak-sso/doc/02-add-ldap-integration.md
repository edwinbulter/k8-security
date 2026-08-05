# Demo: Keycloak met LDAP User Federation in Kubernetes (Kind)

Dit stappenplan beschrijft hoe je binnen een lokaal `kind` Kubernetes-cluster een OpenLDAP-server uitrolt en deze koppelt als User Federation-bron binnen Keycloak.

---

## Stap 1: OpenLDAP uitrollen in het cluster

Maak een bestand genaamd `ldap-deployment.yaml`. Hierin configureren we een OpenLDAP-instantie met de domeinstructuur `dc=nedcar,dc=nl`.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: openldap
  labels:
    app: openldap
spec:
  replicas: 1
  selector:
    matchLabels:
      app: openldap
  template:
    metadata:
      labels:
        app: openldap
    spec:
      containers:
      - name: openldap
        image: osixia/openldap:1.5.0
        ports:
        - containerPort: 389
          name: ldap
        env:
        - name: LDAP_ORGANISATION
          value: "NEDCAR"
        - name: LDAP_DOMAIN
          value: "nedcar.nl"
        - name: LDAP_ADMIN_PASSWORD
          value: "adminpassword"
---
apiVersion: v1
kind: Service
metadata:
  name: openldap
spec:
  ports:
  - port: 389
    targetPort: 389
    name: ldap
  selector:
    app: openldap
```

Pas het manifest toe binnen je cluster:
```bash
kubectl apply -f ldap-deployment.yaml
```

---

## Stap 2: Netwerkverbinding via Kubernetes DNS

Omdat Keycloak en OpenLDAP in hetzelfde cluster draaien, hoef je geen poorten naar je host-machine te forwarden. Keycloak kan de LDAP-server direct bereiken via de interne CoreDNS-naam van de Kubernetes-service:

```text
ldap://openldap:389
```

---

## Stap 3: User Federation configureren in Keycloak

1. Log in op de Keycloak Admin Console.
2. Selecteer de gewenste **Realm** in de linkerbovenhoek.
3. Klik in het linkermenu op **User Federation**.
4. Klik op de knop **Add provider** en selecteer **ldap**.

### Geef de volgende parameters op:

| Veld | Waarde |
| :--- | :--- |
| **UI Display Name** | `Centrale-LDAP` |
| **Vendor** | `Other` (of OpenLDAP) |
| **Connection URL** | `ldap://openldap:389` |
| **Bind DN** | `cn=admin,dc=nedcar,dc=nl` |
| **Bind Credential** | `adminpassword` |

### Gebruikersinstellingen (User Object Settings):

| Veld | Waarde |
| :--- | :--- |
| **Edit Mode** | `READ_ONLY` |
| **Users DN** | `dc=nedcar,dc=nl` |
| **Username LDAP attribute** | `uid` |
| **RDN LDAP attribute** | `uid` |
| **UUID LDAP attribute** | `entryUUID` |
| **User Object Classes** | `inetOrgPerson, organizationalPerson, person` |

Klik onderaan op **Save**. Test daarna de verbinding via de knoppen **Test connection** en **Test authentication** bovenin het scherm.

---

## Stap 4: Testgebruiker aanmaken in OpenLDAP

Maak een lokaal LDIF-bestand aan genaamd `user.ldif` om een testgebruiker te definiëren:

```text
dn: uid=edwin,dc=nedcar,dc=nl
objectClass: top
objectClass: person
objectClass: organizationalPerson
objectClass: inetOrgPerson
cn: Edwin
sn: Bulter
uid: edwin
userPassword: MijnGeheimWachtwoord123
```

Kopieer en injecteer de gebruiker direct in de actieve LDAP-pod met de volgende commando's:

```bash
# Sla de naam van de actieve pod op in een variabele
LDAP_POD=\$(kubectl get pod -l app=openldap -o jsonpath='{.items[0].metadata.name}')

# Kopieer het bestand naar de container
kubectl cp user.ldif \$LDAP_POD:/tmp/user.ldif

# Voer ldapadd uit binnen de container om de data te importeren
kubectl exec -it \$LDAP_POD -- ldapadd -x -D "cn=admin,dc=nedcar,dc=nl" -w adminpassword -f /tmp/user.ldif
```

---

## Stap 5: Verificatie

1. Ga in de Keycloak Admin Console naar **Users**.
2. Klik op **View all users** of zoek specifiek naar `edwin`.
3. De gebruiker verschijnt nu in Keycloak.

Zodra je via je bestaande `oauth2-proxy` inlogt met deze gebruiker, zal Keycloak de authenticatie realtime valideren tegen de OpenLDAP-pod.
