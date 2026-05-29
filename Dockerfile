# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests clean package

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/breakout-detector-1.0.0.jar app.jar
# Render injects PORT; the app reads it via server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["sh","-c","java -XX:MaxRAMPercentage=75 -jar app.jar"]
