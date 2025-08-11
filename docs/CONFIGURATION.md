# Configuration Guide

All configurable properties (with defaults) are consolidated here. Override via `application.yml`, environment variables, or command line `--property=value`.

## Spring / Kafka Streams Core
Property | Default | Notes
---------|---------|------
`spring.kafka.bootstrap-servers` | `kafka:9092` | Adjust for local Docker vs cloud cluster.
`spring.kafka.streams.application-id` | `cibil-error-monitor-app` | Unique per environment to separate state stores.
`spring.kafka.streams.properties.commit.interval.ms` | 1000 | Frequency of committing offsets.

## Aggregation & Backpressure
Property | Default | Description
---------|---------|------------
`app.aggregation.max-buffered-minutes` | 180 | Max distinct minute buckets retained in state store before load shedding.
`app.aggregation.on-overflow` | DROP | DROP (remove oldest) or FAIL (crash application).

## Database Persistence
Property | Default | Description
---------|---------|------------
`spring.datasource.url` | (see sample) | Oracle JDBC URL.
`spring.datasource.username` |  | DB user.
`spring.datasource.password` |  | DB password.
`app.db.retry.max-attempts` | 3 | Attempts for DB operations before giving up.
`app.db.retry.backoff-ms` | 500 | Base backoff (linear: attempt * backoffMs).

## Circuit Breaker (Trip)
Property | Default | Description
---------|---------|------------
`app.circuit.threshold.error-rate` | 50.0 | Trip if avg error rate (%) over last 5 minutes exceeds this.
`app.circuit.threshold.min-total` | 10 | Require at least this many messages (sum) to trip.

## Circuit Breaker (Reset)
Property | Default | Description
---------|---------|------------
`app.circuit.reset.interval-minutes` | 15 | Minimum minutes between reset attempts while tripped.
`app.circuit.reset.error-rate` | 5.0 | Reset if avg error rate (%) is below this.
`app.circuit.reset.min-total` | 10 | Require this many messages over last 5 minutes to reset.

## Logging
Set via `logging.level` in `application.yml`. For verbose internals:
```
logging.level.com.example=DEBUG
```

## Environment Variable Mapping Examples
YAML Key -> Env Var (Spring Boot relaxed binding):
- `spring.datasource.url` -> `SPRING_DATASOURCE_URL`
- `app.aggregation.max-buffered-minutes` -> `APP_AGGREGATION_MAX-BUFFERED-MINUTES` (hyphen preserved; can also use underscore variant `APP_AGGREGATION_MAX_BUFFERED_MINUTES`)
- `app.circuit.threshold.error-rate` -> `APP_CIRCUIT_THRESHOLD_ERROR-RATE` or `APP_CIRCUIT_THRESHOLD_ERROR_RATE`

## Command Line Override Examples
```
java -jar app.jar --app.aggregation.on-overflow=FAIL --app.circuit.threshold.error-rate=60
```

## Recommended Production Overrides
- Set unique `spring.kafka.streams.application-id` per environment.
- Use `processing.guarantee=exactly_once_v2` if adding downstream side effects.
- Tighten retry/backoff based on DB SLAs.
- Externalize credentials (do not hardcode in YAML under version control).

## Validating Configuration at Runtime
- Check resolved properties with `/actuator/env` (if you expose it carefully) or log them at startup.
- Monitor logs for overflow / breaker events to ensure thresholds behave as expected.
