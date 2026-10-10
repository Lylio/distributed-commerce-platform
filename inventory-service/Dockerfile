# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY inventory-service/pom.xml inventory-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -f inventory-service/pom.xml dependency:go-offline
COPY inventory-service/src inventory-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -f inventory-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system commerce && useradd --system --gid commerce commerce
WORKDIR /app
COPY --from=build --chown=commerce:commerce /build/inventory-service/target/inventory-service-1.0.0-SNAPSHOT-exec.jar app.jar
USER commerce
EXPOSE 8082
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 CMD curl -fsS http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
