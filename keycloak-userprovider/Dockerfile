FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY provider/pom.xml .
RUN mvn dependency:go-offline -B

COPY provider/src ./src
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:24.0.0

COPY --from=builder /build/target/user-storage-jpa-example.jar /opt/keycloak/providers/user-storage-jpa-example.jar

# Configure custom datasource for User Storage Provider
RUN printf '\n\
# Custom datasource for User Storage Provider\n\
quarkus.datasource.user-store.db-kind=postgresql\n\
quarkus.datasource.user-store.username=postgres\n\
quarkus.datasource.user-store.password=adminpassword\n\
quarkus.datasource.user-store.jdbc.url=jdbc:postgresql://postgres:5432/userdb\n\
quarkus.datasource.user-store.jdbc.transactions=xa\n' >> /opt/keycloak/conf/quarkus.properties
