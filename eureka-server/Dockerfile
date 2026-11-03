# syntax=docker/dockerfile:1
# =============================================================================
# Eureka Server - production Docker image
#
# Two stages:
#   1. build   - compiles the app with Maven and splits the JAR into layers
#   2. runtime - small JRE-only image that runs the app as a non-root user
# Only stage 2 ends up in the final image: no Maven, no JDK, no source code.
#
# Build (from the eureka-server folder):   docker build -t eureka-server:1.0.0 .
# Run:                                      docker run -d -p 8761:8761 --name eureka-server eureka-server:1.0.0
# =============================================================================


# -----------------------------------------------------------------------------
# Stage 1: build
# -----------------------------------------------------------------------------
# Official Maven image with JDK 21 (same Java version as <java.version> in pom.xml)
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy pom.xml first, then the source code: Docker re-runs only the steps
# after the first file that changed.
COPY pom.xml .
COPY src ./src

# Build the JAR. Tests run in CI, not during the image build (-DskipTests).
# The cache mount keeps downloaded Maven dependencies between builds, so
# rebuilds don't download the internet again. (It is NOT part of the image.)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests

# Split the Spring Boot JAR into layers (dependencies, loader, application).
# Dependencies rarely change, so their layer is reused; a code change only
# replaces the small application layer -> faster pushes and pulls.
RUN cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted


# -----------------------------------------------------------------------------
# Stage 2: runtime
# -----------------------------------------------------------------------------
# JRE only (no compiler), on Alpine Linux: a small image with a small attack surface.
FROM eclipse-temurin:21-jre-alpine

# Never run as root: a dedicated user with a fixed numeric id (1001),
# which Kubernetes can verify with "runAsNonRoot".
RUN addgroup -S -g 1001 spring \
    && adduser -S -u 1001 -G spring spring

WORKDIR /app

# The only folder the app may write to: its log files (see LOG_DIR below).
RUN mkdir -p /app/logs && chown spring:spring /app/logs

# Copy the layers from stage 1, least frequently changed first.
# The files stay owned by root: the app can read but never modify its own code.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# LOG_DIR: where the centralized-logging configuration writes the JSON log file.
# JAVA_TOOL_OPTIONS: JVM settings for containers, overridable with "docker run -e":
#   MaxRAMPercentage=75     use up to 75 % of the CONTAINER's memory limit for the heap
#   ExitOnOutOfMemoryError  crash cleanly on OOM so Docker/Kubernetes restarts the container
ENV LOG_DIR=/app/logs \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER 1001:1001

# Documentation only: the port the app listens on (server.port in application.yml)
EXPOSE 8761

# Docker marks the container "healthy" only when Spring Boot reports UP.
# wget is part of Alpine; start-period gives the JVM time to start.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8761/actuator/health | grep -q '"status":"UP"' || exit 1

# Exec form (JSON array): java runs as PID 1 and receives "docker stop" (SIGTERM)
# directly -> Spring Boot shuts down gracefully.
ENTRYPOINT ["java", "-jar", "app.jar"]
