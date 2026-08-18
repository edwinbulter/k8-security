FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY provider/pom.xml .
RUN mvn dependency:go-offline -B

COPY provider/src ./src
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:24.0.0

COPY --from=builder /build/target/user-storage-jpa-example.jar /opt/keycloak/providers/user-storage-jpa-example.jar
