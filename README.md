# Cibil Error Monitor (Maven)

This is a starter Spring Boot (Java 21) project demonstrating a Kafka Streams topology that:
- consumes JSON messages from Kafka topic `Error-topic`
- computes error statistics over a 5-minute sliding window with 1-minute advance
- writes aggregate stats into `error_stats` Oracle table
- toggles `circuitbreaker_status` flag when failure rate > 50% (trip) and resets to false after 15 minutes of sustained low error rate (<=5%)

## What you get
- Maven project
- Docker Compose to run Kafka locally (for testing)
- SQL scripts to create Oracle tables
- Sample producer script (bash) to push test messages

## How to run
1. Start Kafka locally:
   ```bash
   docker compose up -d
   ```
2. Create Oracle tables (use `sql/create_tables.sql`)
3. Ensure Oracle JDBC driver is available:
   - Many Oracle drivers are not on Maven Central. Install `ojdbc11.jar` into your local Maven repo:
     ```
     mvn install:install-file -Dfile=path/to/ojdbc11.jar -DgroupId=com.oracle.database.jdbc        -DartifactId=ojdbc11 -Dversion=21.9.0.0 -Dpackaging=jar
     ```
   - Then uncomment the dependency in `pom.xml`.
4. Configure `src/main/resources/application.yml` with your Kafka and Oracle settings.
5. Run the app:
   ```
   mvn spring-boot:run
   ```
6. Produce test messages:
   ```bash
   ./scripts/produce-sample.sh
   ```

## Notes
- The project uses a global grouping (single key) to compute error rate across all messages. If you want per-product or per-client metrics, change grouping key accordingly.
- For production, consider persisting circuit breaker state in a compacted Kafka topic or distributed store for multi-instance safety.


## Pushing to GitHub

To create a GitHub repository and push:

```bash
git init
git add .
git commit -m "Initial commit - Cibil error monitor"
# create remote repo via GitHub UI or use gh cli
gh repo create <your-org-or-username>/cibil-error-monitor --public --source=. --remote=origin --push
```

If you don't have `gh` (GitHub CLI), create the repo on GitHub and then:

```bash
git remote add origin https://github.com/<your-username>/cibil-error-monitor.git
git branch -M main
git push -u origin main
```
