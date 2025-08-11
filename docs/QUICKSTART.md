# Quick Start

This is a concise checklist to get the Cibil Error Monitor running locally.

## 1. Prerequisites
- JDK 21
- Maven 3.9+
- Docker (for Kafka & optionally Oracle DB)
- Oracle XE / or alternative DB reachable via JDBC (adjust `application.yml`).

## 2. Build
```
mvn clean package
```
Jar produced: `target/cibil-error-monitor-0.1.0.jar`.

## 3. Run Supporting Services
Spin up (or connect to existing) Kafka and Oracle. Example (Kafka only):
```
docker compose up -d
```
Ensure external `kafka-net` network exists if using provided compose file.

## 4. Configure
Edit `src/main/resources/application.yml` OR override via env vars:
```
SPRING_DATASOURCE_URL=jdbc:oracle:thin:@//localhost:1521/XEPDB1
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
APP_KAFKA_ERROR-TOPIC=error-monitor-events
```

## 5. Run App (Dev Mode)
```
mvn spring-boot:run
```
Or run jar:
```
java -jar target/cibil-error-monitor-0.1.0.jar
```

## 6. Produce Test Events
```
./scripts/produce-sample.sh
```
(Modify script if your Kafka bootstrap servers differ.)

## 7. Verify
- Logs should show minute flushes ("Flushing minute" lines) every completed minute.
- Oracle table `ERROR_STATS` should accumulate rows.
- Breaker trips once last 5 minutes exceed thresholds (look for "Circuit breaker tripped").

## 8. Stop
```
Ctrl+C (app)
docker compose down   # if you started compose
```

For deeper architecture details see `docs/OVERVIEW.md`.
