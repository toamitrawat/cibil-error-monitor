# Cibil Error Monitor (Maven)

This is a Spring Boot (Java 21) project implementing a Kafka Streams topology that:
- Consumes JSON messages from a Kafka topic (default `error-monitor-events`).
- Performs custom per-minute (wall-clock) aggregation of success vs failure counts with gap (zero) minute handling.
- Persists one row per minute into the `error_stats` Oracle table (including empty minutes to maintain a contiguous timeline).
- Evaluates a circuit breaker using the last 5 persisted minutes’ average error rate (trip & reset logic) and logs transitions in `circuitbreaker_status`.

## What you get
- Maven project (Java 21 / Spring Boot 3)
- Kafka Streams based custom minute aggregation (no high-level sliding window API)
- Backpressure controls: bounded in-memory minute buffer with DROP or FAIL policy
- Retry logic for DB writes with linear backoff
- Dockerfile + (minimal) docker-compose
- SQL scripts to create Oracle tables
- Sample producer script (bash) to push test messages
- Documentation in `docs/` (overview, quick start, configuration)

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

## Documentation
- High-level architecture & design: `docs/OVERVIEW.md`
- Quick start checklist: `docs/QUICKSTART.md`
- Configuration reference: `docs/CONFIGURATION.md`

## Notes
- The topology intentionally forces a single partition (key=`ALL`) to guarantee exactly one writer instance. Adjust if you need parallelism.
- For multi-instance breaker visibility, you may externalize breaker state to a compacted topic or a shared cache.

## Persistence Layer (Spring Data JDBC)
Originally set up with Spring Data JPA / Hibernate, the project now uses Spring Data JDBC for a simpler, more explicit data model:
- No JPA entity manager, lazy loading, or dirty checking. Each repository method executes direct SQL via Spring Data JDBC.
- Entities (`ErrorStats`, `CircuitBreakerStatus`) map directly to tables using `@Table` and `@Column` from `spring-data-relational`.
- Identity columns defined in `sql/create_tables.sql` are used; the `id`/`seqnum` fields are populated on insert.
- Custom queries currently rely on derived method names (e.g. `findTop5ByOrderByEndTimeDesc`). If you need more complex queries, add `@Query` methods or a lightweight custom repository.
- Transactions use Spring's `@Transactional` (backed by `DataSourceTransactionManager`). Keep transactions brief—no persistence context caching.

If you reintroduce JPA, revert dependencies (`spring-boot-starter-data-jpa`) and restore `spring.jpa.*` settings in `application.yml`.

## License
See `LICENSE` for details.


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
