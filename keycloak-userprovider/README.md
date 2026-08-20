# Keycloak User Storage Provider met PostgreSQL

Dit project demonstreert een custom **Keycloak User Storage Provider** die gebruikers uit een externe **PostgreSQL** database leest in plaats van Keycloak's ingebouwde gebruikersopslag. Het is gebaseerd op de officiële [Keycloak user-storage-jpa quickstart](https://github.com/keycloak/keycloak-quickstarts/tree/main/extension/user-storage-jpa). Voor een beter begrip is het aan te raden om eerst [Server development - User Storage SPI](https://www.keycloak.org/docs/latest/server_development/index.html#_user-storage-spi) te lezen.

---

## 🧠 Wat is een User Storage Provider?

Keycloak ondersteunt *User Federation* via een SPI (Service Provider Interface). Een custom User Storage Provider stelt je in staat om gebruikers uit een willekeurige externe bron (database, LDAP, REST API) beschikbaar te maken in Keycloak, zonder ze te dupliceren naar Keycloak's eigen opslag.

In deze demo leest de provider uit een PostgreSQL database `userdb` met een eenvoudige `users` tabel. De 10 testgebruikers (`user-1` t/m `user-10`) zijn direct in de Admin Console van Keycloak te bekijken en te doorzoeken.

---

## 🏗️ Architectuur

```text
[ Browser / Admin Console ]
          │
          ▼
   [ Keycloak Pod ] ──── (JPA / Hibernate) ──── [ PostgreSQL Pod ]
          │                        │                     │
   (User Storage SPI)        (datasource:           (database: userdb
          │                  "user-store")          tabel: users)
          ▼
   [ MyUserStorageProvider ]
     - getUserByUsername()
     - getUserByEmail()
     - isValid() (password check)
     - searchForUserStream()
```

---

## 📂 Projectstructuur

```text
keycloak-userprovider/
├── Dockerfile                          # Multi-stage: Maven build + Keycloak image
├── README.md                           # Dit bestand
├── provider/
│   ├── pom.xml                         # Maven build configuratie
│   └── src/main/
│       ├── java/org/keycloak/quickstart/storage/user/
│       │   ├── MyExampleUserStorageProviderFactory.java
│       │   ├── MyUserStorageProvider.java
│       │   ├── UserAdapter.java
│       │   └── UserEntity.java
│       └── resources/META-INF/
│           ├── persistence.xml         # JPA config (PostgreSQL dialect)
│           └── services/org.keycloak.storage.UserStorageProviderFactory
├── k8/
│   ├── 01-postgres.yaml                # PostgreSQL + init SQL (10 users)
│   └── 02-keycloak.yaml                # Keycloak met custom provider image
└── doc/
    └── 01-setup-userprovider-demo.md   # Stap-voor-stap handleiding
```

---

## 🚀 Snel aan de slag

Volg de volledige installatiehandleiding:

👉 **[Setup Handleiding](doc/01-setup-userprovider-demo.md)**

---

## 🔐 Bijdrage aan Zero-Trust

Deze demo versterkt het Zero-Trust principe door *federated identity* te demonstreren: gebruikers hoeven niet in Keycloak zelf te worden aangemaakt, maar worden live uit een externe autoritatieve bron gehaald. Dit past bij het uitgangspunt *Never trust, always verify* — Keycloak vertrouwt de externe database als *source of truth* en verifieert elke inlogpoging realtime.
