# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies in a separate layer (re-runs only when pom.xml changes)
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Compile and package the application (tests skipped for speed)
COPY src ./src
RUN mvn -B -DskipTests package

# ---------- Stage 2: run ----------
FROM eclipse-temurin:21-jre

# Run as a non-root user
RUN useradd --system --uid 1001 --create-home spring

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R spring:spring /app

USER spring
EXPOSE 8080

# Values below can all be overridden at runtime with -e / docker-compose environment:
#   DATABASE_URL, DB_USER, DB_PASS, JWT_SECRET, CORS_ALLOWED_ORIGINS, SERVER_PORT
ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
