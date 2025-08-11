# Cibil Error Monitor – Detailed Overview

This document provides a deeper architectural and operational view than the root README. It explains the stream processing model, persistence model, circuit breaker logic, deployment/runtime concerns, and extension points.

## 1. Problem Statement
Continuously ingest status events (success/failure) from a Kafka topic, persist per‑minute counts for audit/analytics, and expose a simple circuit breaker mechanism that trips when recent error rate is high and later resets after a period of stability.

## 2. High-Level Architecture
```
Kafka Topic (error-monitor-events)
        |
   Kafka Streams Topology (ErrorStreamTopology)
        |  (per-minute aggregation in state store)
        v
Oracle DB (error_stats, circuitbreaker_status)
```
Supporting components:
- Spring Boot runtime (Actuator, dependency injection, config binding)
- Spring Data JDBC for persistence
- In‑memory + DB backed circuit breaker state
- Backpressure & buffer overflow policy (DROP or FAIL) for minute buckets

## 3. Stream Topology Flow
1. Consume raw JSON messages from the configured topic (default `error-monitor-events`).
2. Repartition all events to a single partition using a constant key (`ALL`) so only one stream task performs aggregation & DB writes (avoids duplicate writes when scaling horizontally). (If you want parallelism, you must introduce partitioning key and handle duplication / idempotency.)
3. Extract `Request.status` field -> classify as `Failure`, other -> success.
4. Accumulate counts into a persistent key-value state store keyed by minute start epoch millis.
5. A wall‑clock punctuation every 5 seconds triggers flush logic:
   - For each completed minute (bucket start < current minute start) persist aggregated counts.
   - After persisting, evaluate circuit breaker using last 5 persisted minutes (sliding window of persisted stats, not in-flight state).
   - Fill gaps: emit zero rows for missing minutes between last flushed minute and most recently completed minute to keep time series complete.
6. Enforce buffer size `app.aggregation.max-buffered-minutes` (default 180). On overflow either:
   - DROP: remove oldest unflushed buckets (warn), or
   - FAIL: throw IllegalStateException to crash (expect orchestrator restart).

## 4. Persistence Model
Tables (see `sql/create_tables.sql`):
- `error_stats`: one row per minute (or zero-count row for gaps). Columns: start_time, end_time, total_message, error_count, error_rate.
- `circuitbreaker_status`: time‑stamped flag transitions (Y/N). Acts as audit log.

Spring Data JDBC repositories:
- `ErrorStatsRepository` – derived query `findTop5ByOrderByEndTimeDesc()`.
- `CircuitBreakerRepository` – derived query for last trip entry.

No ORM session caching; every call hits DB directly.

## 5. Circuit Breaker Logic
Configuration (see `application.yml`):
- Trip criteria: average error rate over last 5 persisted minutes > `app.circuit.threshold.error-rate` and combined total > `app.circuit.threshold.min-total`.
- Reset scheduling: while tripped, only evaluate for reset every `app.circuit.reset.interval-minutes` (default 15) minutes since trip time.
- Reset criteria: over last 5 persisted minutes average error rate < `app.circuit.reset.error-rate` and total > `app.circuit.reset.min-total`.
State is duplicated:
- In-memory atomics for fast decision.
- DB rows appended for audit and cross-instance visibility (only writer instance changes it due to single partition design).

## 6. Reliability & Backpressure
- Buffered minute buckets limited by `app.aggregation.max-buffered-minutes`.
- On DB write failure, the minute bucket is retained and retried on next punctuation (`runWithRetry` inside flush).
- Retries: linear backoff (attempt * backoff-ms), attempts = `app.db.retry.max-attempts`.
- Zero-filling ensures consistent timeline even with idle periods.

## 7. Time Semantics
- Aggregation uses record timestamp (`record.timestamp()`). If producers supply event time you get coherent minute alignment; otherwise broker ingestion time.
- Flush trigger is wall-clock punctuation every 5 seconds.
- Timestamps persisted are converted to Asia/Kolkata (IST) in `ErrorService` before saving.

## 8. Configuration Reference
Property | Purpose | Default
---------|---------|--------
`app.kafka.error-topic` | Source Kafka topic | `error-monitor-events`
`app.db.retry.max-attempts` | DB retry attempts | 3
`app.db.retry.backoff-ms` | Base backoff (linear) | 500
`app.aggregation.max-buffered-minutes` | Max in-memory minute buckets | 180
`app.aggregation.on-overflow` | Overflow policy (DROP/FAIL) | DROP
`app.circuit.threshold.error-rate` | Trip threshold (%) | 50.0
`app.circuit.threshold.min-total` | Min messages (trip) | 10
`app.circuit.reset.interval-minutes` | Interval between reset attempts | 15
`app.circuit.reset.error-rate` | Reset threshold (%) | 5.0
`app.circuit.reset.min-total` | Min messages (reset) | 10

## 9. Scaling & Deployment
Current single-writer assumption: all events are funneled to one partition to have exactly one aggregator/writer. If you scale horizontally (multiple container instances) they will all consume but only one will own that partition at a time.

To scale throughput:
- Partition by a dimension (e.g., product) and write multi-row minutes per key.
- Introduce idempotent upserts (unique constraint on (key, minute_start)) or use Kafka Streams exactly-once semantics (enable EOS config) plus transactional writes to a sink topic driving a separate writer.

## 10. Failure Modes & Recovery
Scenario | Behavior
---------|---------
DB transient failure | Retries then leaves bucket for next cycle
DB prolonged outage + DROP policy | Buckets may be dropped once buffer overflows (data loss risk)
Overflow with FAIL policy | Application throws and should restart (persistent store keeps unflushed buckets until local state dir removed)
Crash before flush | Buckets for current & unflushed minutes remain only in local state; on restart they are replayed (persistent key-value store)

## 11. Extending
Use Cases | How
----------|----
Add per-client metrics | Derive key instead of constant "ALL" and adjust table schema (add key column)
Expose REST for breaker state | Add Controller reading latest `circuitbreaker_status`
Export Prometheus metrics | Add Micrometer & custom gauges for `droppedMinutesCount`, breaker flag
Switch DB | Replace datasource URL & driver; adjust SQL if identity semantics differ

## 12. Local Development Tips
- If you modify overflow/trip thresholds, keep an eye on logs (`com.example` at DEBUG) for detailed evaluation messages.
- To simulate failures, stop Oracle container or inject exceptions in `ErrorService.saveStats`.
- For deterministic tests, consider providing a TestDouble for `ErrorService` capturing persisted stats in-memory.

## 13. Observability
Add (optional) metrics:
- Gauge: circuit breaker flag
- Counter: dropped minutes
- Timer: DB save latency

## 14. Security Considerations
- Keep DB credentials out of VCS—use environment variables or externalized config for production.
- Consider enabling `processing.guarantee=exactly_once_v2` if you add more critical side-effects.

## 15. Future Improvements (Backlog)
- Add integration tests with Testcontainers (Kafka + Oracle XE or compatible DB like H2 in Oracle mode)
- Expose breaker status via REST endpoint
- Implement exponential backoff jitter for retries
- Add unique index for `error_stats(start_time)` and switch to upsert semantics
- Replace single-partition design with sharded aggregation + consolidation

---
For quick start instructions see the root `README.md`. For contributing see `CONTRIBUTING.md`.
