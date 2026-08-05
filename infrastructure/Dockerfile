# Build jar file
FROM gradle:8.8.0-jdk21 AS builder

# copy project file
COPY . .

RUN gradle clean build --no-demon

# stack 2 run jar
FROM eclipse-temurin:21-jre-jummy
WORKDIR /app

COPY --from=builder /app/build/libs/genlink_kh-1.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]