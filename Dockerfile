FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/cibil-error-monitor-*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
