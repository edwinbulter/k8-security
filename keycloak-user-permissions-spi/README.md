# Keycloak User Permissions SPI met PostgreSQL

Dit project demonstreert een custom **Keycloak Protocol Mapper** die permissions uit een externe **PostgreSQL** database als JWT claims injecteert. Het bouwt voort op de [User Storage Provider demo](../keycloak-userprovider/README.md) en voegt een rol-permissie model toe.

Daarnaast bevat het twee **Node.js Express** web clients die via OIDC authenticeren en de permissions uit het JWT token tonen. SSO werkt automatisch omdat beide clients in dezelfde Keycloak realm zitten.

Voor een beter begrip is het aanbevolen om eerst [Server development - User Storage SPI](https://www.keycloak.org/docs/latest/server_development/index.html#_user-storage-spi) te lezen.

## Implementatie

De `PermissionProtocolMapper` is geimplementeerd volgens het standaard Keycloak patroon voor Protocol Mappers. Voor de implementatie is gekeken naar de broncode van Keycloak zelf, specifiek de implementaties van `AbstractOIDCProtocolMapper` in de Keycloak sources: https://github.com/keycloak/keycloak (zie `services/src/main/java/org/keycloak/protocol/oidc/mappers/`).

## Architectuur

```text
[ PostgreSQL (userdb) ]
   users | roles | permissions | user_roles | role_permissions
         │
         ├── User Storage Provider ── federates users into Keycloak
         │
         └── Permission Protocol Mapper ── reads permissions at token issuance
                    │
                    ▼
              [ Keycloak ] (keycloak-permissions.localhost)
                    │
            (OIDC + SSO)
                    │
          ┌────────┴────────┐
          ▼                 ▼
   [ Web Client A ]   [ Web Client B ]
   client-a.localhost  client-b.localhost
```

## Test Data

**10 users, 4 roles, 10 permissions** — meerdere users delen dezelfde roles.

| User | Roles | Permissions |
|------|-------|-------------|
| user-1 | role-1 | permission-1, permission-2, permission-3 |
| user-2 | role-2 | permission-4, permission-5, permission-6 |
| user-3 | role-3 | permission-7, permission-8 |
| user-4 | role-4 | permission-9, permission-10 |
| user-5 | role-1, role-2 | permission-1 t/m permission-6 |
| user-6 | role-2, role-3 | permission-4 t/m permission-8 |
| user-7 | role-1, role-3 | permission-1, 2, 3, 7, 8 |
| user-8 | role-4 | permission-9, permission-10 |
| user-9 | role-2, role-4 | permission-4, 5, 6, 9, 10 |
| user-10 | role-1, role-4 | permission-1, 2, 3, 9, 10 |

## Setup

Zie de [Setup Handleiding](./doc/01-setup-permissions-demo.md) voor een volledige stap-voor-stap uitleiding.

## Opruimen

```bash
kubectl delete namespace keycloak-permissions
```
