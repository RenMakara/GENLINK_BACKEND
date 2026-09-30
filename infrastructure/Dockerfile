# Build the application JAR.
FROM gradle:8.8.0-jdk21 AS builder

# Copy source and build files.
COPY . .

RUN gradle clean build --no-demon

# Run the JAR on Java 21.
FROM eclipse-temurin:21-jre-jummy
WORKDIR /app

COPY --from=builder /app/build/libs/genlink_kh-1.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
