# syntax=docker/dockerfile:1.6

# ---- Build stage ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Compile and package
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user
RUN groupadd --system app && useradd --system --gid app --create-home app
USER app

COPY --from=build /workspace/target/purchase-tracker-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
