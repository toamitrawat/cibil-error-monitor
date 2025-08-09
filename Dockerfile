FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY target/cibil-error-monitor-*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
