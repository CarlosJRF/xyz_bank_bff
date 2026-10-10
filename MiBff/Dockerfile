# ---- Build stage: compile and package the Spring Boot jar with Maven + JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Dependencies first: this layer is reused while pom.xml does not change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests \
    && cp target/*.jar app.jar

# ---- Runtime stage: Java 21 JRE only, non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER spring
EXPOSE 8443
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Every route needs credentials, so a 401 over TLS still proves the BFF is serving.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
    CMD wget -q -S --no-check-certificate -O /dev/null https://localhost:8443/ 2>&1 | grep -q 'HTTP/' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
