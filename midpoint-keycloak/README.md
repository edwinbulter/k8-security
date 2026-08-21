# midPoint + Keycloak: Identity Governance & Authentication

Deze demo combineert **Evolveum midPoint 4.8** (Identity Governance & Administration) met **Keycloak 24** (authenticatie en JWT token uitgifte). midPoint fungeert als "single source of truth" voor gebruikers en role assignments, terwijl Keycloak gebruikers authenticeert en permissies als JWT claims injecteert.

## Doel

Aantonen dat een IGA-platform (midPoint) naadloos samenwerkt met een identity provider (Keycloak):
- **midPoint** beheert de gebruikerslicycle: aanmaken, wijzigen, verwijderen en role assignments
- **Keycloak** leest gebruikers live uit de gedeelde PostgreSQL database via een User Storage Provider
- **Permission Protocol Mapper** injecteert permissies (afgeleid van rollen) in JWT tokens bij elke login

## Wat regelt midPoint?

| Verantwoordelijkheid | midPoint | Keycloak |
|----------------------|----------|----------|
| Gebruikers aanmaken/wijzigen/verwijderen | ✅ | ❌ |
| Rollen toewijzen aan gebruikers | ✅ | ❌ |
| Reconciliation (detecteren handmatige wijzigingen) | ✅ | ❌ |
| Audit trail van wijzigingen | ✅ | ❌ |
| Wachtwoord verificatie | ❌ | ✅ |
| Sessiebeheer en SSO | ❌ | ✅ |
| JWT token uitgifte | ❌ | ✅ |
| Permissies in JWT injecteren | ❌ | ✅ |

## Architectuur

```text
[ midPoint ] ── provisions users + role assignments ──→ [ PostgreSQL (userdb) ]
   IGA platform                                              users, roles, permissions
   http://midpoint.localhost                                     │
                                                                 ├── Keycloak User Storage Provider
                                                                 └── Permission Protocol Mapper
                                                                        │
                                                                   [ Keycloak ]
                                                                   http://keycloak-midpoint.localhost
                                                                        │
                                                                  (OIDC + SSO)
                                                                        │
                                                                  ┌──────┴──────┐
                                                                  ▼             ▼
                                                           [ Client A ]   [ Client B ]
                                                           client-a-mid.   client-b-mid.
                                                           localhost       localhost
```

## Data Model

midPoint provisioneert naar twee tabellen in PostgreSQL `userdb`:

- **`users`** — gebruikersgegevens (id, username, email, password, phone)
- **`user_roles`** — koppeling tussen gebruikers en rollen

De volgende tabellen worden handmatig geseed (niet door midPoint beheerd):

- **`roles`** — 4 rollen (role-1 t/m role-4)
- **`permissions`** — 10 permissies (permission-1 t/m permission-10)
- **`role_permissions`** — koppeling rollen → permissies

## Demo Scenario

1. **Gebruiker aanmaken** — In midPoint: maak een nieuwe gebruiker aan → midPoint provisioneert naar PostgreSQL → gebruiker kan direct inloggen via Keycloak
2. **Rol toewijzen** — In midPoint: ken een rol toe aan een gebruiker → bij volgende login bevat het JWT token de bijbehorende permissies
3. **Rol intrekken** — In midPoint: verwijder een rol van een gebruiker → bij volgende login zijn die permissies verdwenen uit het token
4. **Gebruiker verwijderen** — In midPoint: verwijder een gebruiker → kan niet meer inloggen
5. **Reconciliation** — Maak handmatig een wijziging in PostgreSQL → midPoint detecteert en herstelt dit

## Setup

Zie de [Setup Handleiding](./doc/01-setup-midpoint-demo.md) voor een volledige stap-voor-stap uitleg.

## Opruimen

```bash
kubectl delete namespace midpoint-keycloak
```

Dit verwijdert alle resources: PostgreSQL, midPoint, Keycloak, web clients, ingresses en de namespace zelf.

## Implementatie

De Keycloak SPI (User Storage Provider en Permission Protocol Mapper) wordt hergebruikt uit de [Keycloak User Permissions SPI demo](../keycloak-user-permissions-spi/). De broncode en documentatie daarvan is leidend voor de SPI implementatie.
