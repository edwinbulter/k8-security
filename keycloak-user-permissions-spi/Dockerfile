FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy provider pom.xml and download dependencies (separate layer for caching)
COPY provider/pom.xml ./provider/
RUN cd provider && mvn dependency:go-offline -B

# Copy mapper pom.xml and download dependencies (separate layer for caching)
COPY mapper/pom.xml ./mapper/
RUN cd mapper && mvn dependency:go-offline -B

# Copy provider sources and build
COPY provider/src ./provider/src
RUN cd provider && mvn clean package -DskipTests

# Copy mapper sources and build
COPY mapper/src ./mapper/src
RUN cd mapper && mvn clean package -DskipTests

# Start from Keycloak base image
FROM quay.io/keycloak/keycloak:24.0.0

# Copy both JARs into the Keycloak providers directory
COPY --from=builder /build/provider/target/permissions-spi-user-storage.jar /opt/keycloak/providers/
COPY --from=builder /build/mapper/target/permission-protocol-mapper.jar /opt/keycloak/providers/

# Configure custom datasource for User Storage Provider
RUN printf '\n\
# Custom datasource for User Storage Provider\n\
quarkus.datasource.user-store.db-kind=postgresql\n\
quarkus.datasource.user-store.username=postgres\n\
quarkus.datasource.user-store.password=adminpassword\n\
quarkus.datasource.user-store.jdbc.url=jdbc:postgresql://postgres:5432/userdb\n\
quarkus.datasource.user-store.jdbc.transactions=xa\n' >> /opt/keycloak/conf/quarkus.properties
