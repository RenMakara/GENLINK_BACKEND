# GenLink KH Spring Boot image, built with Java 21 and Gradle.
# Multi-stage build keeps the JDK and source out of the runtime image.

# Build with: docker compose build app

# Build stage
# Use Java 21 to match the Gradle toolchain.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy build files first so dependency layers survive source edits.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Strip Windows line endings before running Gradle on Linux.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Warm Gradle's cache; the actual build reports dependency failures.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src

# Build only the executable JAR; tests run in CI.
RUN ./gradlew --no-daemon clean bootJar -x test

# Normalize the artifact path for the runtime stage.
RUN cp build/libs/*.jar /workspace/app.jar

# Runtime base with a JRE only.
FROM eclipse-temurin:21-jre-jammy AS runtime-base
WORKDIR /app

# Run as an unprivileged user to limit container access.
RUN groupadd --system spring && useradd --system --gid spring spring

USER spring

# Compose publishes this port; the app listens on 8888.
EXPOSE 8888

# Size the heap from the container limit, not host RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Use exec so Java receives SIGTERM and shuts down cleanly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# Deployment targets
FROM runtime-base AS prebuilt-runtime
# CI supplies the built JAR so deployment needs no JDK or Gradle.
COPY --chown=spring:spring .deploy/app.jar app.jar

# Keep the source-build target as the default for local builds and recovery.
FROM runtime-base AS runtime
COPY --from=build --chown=spring:spring /workspace/app.jar app.jar
