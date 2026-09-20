# GenLink KH — Spring Boot 3.3.5 / Java 21, built with Gradle.
#
# Multi-stage: a JDK image compiles the jar, then a much smaller JRE image
# runs it. The JDK, Gradle, and the whole source tree never reach the
# shipped image — that is ~400 MB smaller and removes a compiler and your
# source code from the production attack surface.
#
# Build:  docker compose build app
# (Docker does the compiling, so the EC2 instance needs no JDK or Gradle.)

# ── Stage 1: build ─────────────────────────────────────────────────────
# Pinned to the same JDK the Gradle toolchain requests (Java 21). Match
# them, or Gradle downloads a second JDK mid-build and slows everything.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Wrapper and build scripts first, source later. Docker caches each layer,
# so editing a .java file no longer re-downloads every dependency — only
# a change to build.gradle does.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./

# gradlew is a shell script. Committed from Windows it can carry CRLF line
# endings, and Linux then fails with a confusing
# "/bin/sh^M: bad interpreter". Strip them and make it executable.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Warm the dependency cache as its own layer. `|| true` because this step
# is only an optimisation — a resolution hiccup here must not fail the
# build, since the real build below would report it properly anyway.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src

# `bootJar`, not `build`: it produces only the executable fat jar.
#   - `build` would also emit genlink_kh-version-1.0.0-PLAIN.jar, which has
#     no Main-Class and dies with "no main manifest attribute" if copied
#     by mistake.
#   - `-x test` keeps deployment fast and hermetic; tests belong in CI,
#     and many of yours need a live database.
RUN ./gradlew --no-daemon clean bootJar -x test

# Normalise the artifact name so the runtime stage never has to know the
# project version. Bare `*.jar` is safe here: only bootJar has run.
RUN cp build/libs/*.jar /workspace/app.jar

# ── Stage 2: runtime base ──────────────────────────────────────────────
# JRE, not JDK — no compiler in production.
FROM eclipse-temurin:21-jre-jammy AS runtime-base
WORKDIR /app

# Run as a normal user. If the app is ever compromised, the attacker lands
# as `spring`, not root. Docker defaults to root, so this must be explicit.
RUN groupadd --system spring && useradd --system --gid spring spring

USER spring

# Documentation only; publishing is decided in docker-compose.yml.
# Matches server.port in application.yaml — 8888, not Spring's default 8080.
EXPOSE 8888

# MaxRAMPercentage instead of a fixed -Xmx: the JVM reads the container's
# cgroup limit, so the heap adapts to whatever mem_limit Compose sets.
# Hardcoding -Xmx risks the JVM sizing itself against the HOST's RAM and
# getting OOM-killed alongside Postgres on a small EC2 instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Exec form (via `sh -c` only for $JAVA_OPTS expansion, then `exec`) so the
# JVM becomes PID 1 and receives SIGTERM directly. Without `exec`, the
# shell swallows the signal and Docker kills the app after a 10s timeout
# instead of letting Spring shut down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# ── Stage 3: deploy targets ────────────────────────────────────────────
# The normal local/standalone build compiles the source in Docker.
FROM runtime-base AS prebuilt-runtime
# CI places the already-tested artifact here before the SSH deploy. This
# target deliberately has no JDK or Gradle dependency, so a small EC2 host
# does not recompile the whole application during deployment.
COPY --chown=spring:spring .deploy/app.jar app.jar

# Keep the source-build target as the default so `docker compose build app`
# still works for local development and manual server recovery.
FROM runtime-base AS runtime
COPY --from=build --chown=spring:spring /workspace/app.jar app.jar
