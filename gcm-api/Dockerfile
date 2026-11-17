# Construction et exécution de l'API GCM (Spring Boot, Java 17)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY gcm-common/pom.xml gcm-common/
COPY gcm-domain/pom.xml gcm-domain/
COPY gcm-documents/pom.xml gcm-documents/
COPY gcm-api/pom.xml gcm-api/
RUN mvn -q -B dependency:go-offline -DexcludeReactor=true || true
COPY gcm-common gcm-common
COPY gcm-domain gcm-domain
COPY gcm-documents gcm-documents
COPY gcm-api gcm-api
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 1001 gcm && mkdir -p /data/ged && chown gcm /data/ged
WORKDIR /app
COPY --from=build /src/gcm-api/target/gcm-api-*.jar app.jar
USER gcm
ENV GCM_STOCKAGE_DIR=/data/ged JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
