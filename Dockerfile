FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY UnipodERPBackend/pom.xml UnipodERPBackend/pom.xml
COPY UnipodERPBackend/.mvn/ UnipodERPBackend/.mvn/
COPY UnipodERPBackend/mvnw UnipodERPBackend/mvnw
COPY UnipodERPBackend/src/ UnipodERPBackend/src/

WORKDIR /workspace/UnipodERPBackend
RUN chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/UnipodERPBackend/target/UnipodERPBackend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
