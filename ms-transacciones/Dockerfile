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
EXPOSE 8094
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Answers 200 as soon as Tomcat and the database are up.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
    CMD wget -q -O /dev/null http://localhost:8094/transacciones/cuenta/101 || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
