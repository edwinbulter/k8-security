FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
# Copy pom.xml only
COPY provider/pom.xml .
# Download dependencies only to reuse them in the next build (separate layer for caching)
RUN mvn dependency:go-offline -B

# Copy sources
COPY provider/src ./src
# Build jar file
RUN mvn clean package -DskipTests

# Start from Keycloak base image
FROM quay.io/keycloak/keycloak:24.0.0

# Copy the built JAR from the builder stage in the keycloak providers directory
COPY --from=builder /build/target/user-storage-jpa-example.jar /opt/keycloak/providers/user-storage-jpa-example.jar

# Configure custom datasource for User Storage Provider
RUN printf '\n\
# Custom datasource for User Storage Provider\n\
quarkus.datasource.user-store.db-kind=postgresql\n\
quarkus.datasource.user-store.username=postgres\n\
quarkus.datasource.user-store.password=adminpassword\n\
quarkus.datasource.user-store.jdbc.url=jdbc:postgresql://postgres:5432/userdb\n\
quarkus.datasource.user-store.jdbc.transactions=xa\n' >> /opt/keycloak/conf/quarkus.properties
