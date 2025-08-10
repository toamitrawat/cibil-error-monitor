package com.example.cibil.service;

import com.example.cibil.entity.CircuitBreakerStatus;
import com.example.cibil.entity.ErrorStats;
import com.example.cibil.repo.CircuitBreakerRepository;
import com.example.cibil.repo.ErrorStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ErrorService {

    private final CircuitBreakerRepository cbRepo;
    private final ErrorStatsRepository statsRepo;

    private static final Logger logger = LoggerFactory.getLogger(ErrorService.class);

    // track in-memory state for the flag and last change time
    private final AtomicReference<Boolean> flag = new AtomicReference<>(false);
    private final AtomicReference<Instant> lastFlagChange = new AtomicReference<>(Instant.EPOCH);
    // next allowed reset attempt time (only meaningful when flag=true)
    private final AtomicReference<Instant> nextResetCheck = new AtomicReference<>(Instant.EPOCH);

    @Value("${app.circuit.threshold.error-rate:50.0}")
    private double tripErrorRate;

    @Value("${app.circuit.threshold.min-total:10}")
    private long minTotal;

    @Value("${app.circuit.reset.interval-minutes:15}")
    private long resetIntervalMinutes;

    @Value("${app.circuit.reset.error-rate:5.0}")
    private double resetErrorRate;

    @Value("${app.circuit.reset.min-total:10}")
    private long resetMinTotal;

    // Lombok @RequiredArgsConstructor generates constructor

    @Transactional
    public void saveStats(Instant start, Instant end, long total, long errors, double rate) {
    ZoneId ist = ZoneId.of("Asia/Kolkata");
    OffsetDateTime startIst = OffsetDateTime.ofInstant(start, ist);
    OffsetDateTime endIst = OffsetDateTime.ofInstant(end, ist);
    logger.info("Saving error stats (IST): start={}, end={}, total={}, errors={}, rate={}", startIst, endIst, total, errors, rate);
    ErrorStats stats = new ErrorStats();
    stats.setStartTime(startIst);
    stats.setEndTime(endIst);
        stats.setTotalMessage(total);
        stats.setErrorCount(errors);
        stats.setErrorRate(rate);
    stats.setCreatedTime(OffsetDateTime.ofInstant(Instant.now(), ist));
        statsRepo.save(stats);
    logger.debug("ErrorStats saved: {}", stats);
    }

    @Transactional
    public void evaluateCircuitBreaker(double latestErrorRate, long latestTotal) {
        // Fetch last 5 entries (including the one just saved). Use repository ordering.
        List<ErrorStats> lastFive = statsRepo.findTop5ByOrderByEndTimeDesc();
        if (lastFive.isEmpty() || lastFive.size() < 5) {
            logger.debug("Not enough data points yet for circuit breaker evaluation. size={}", lastFive.size());
            return; // need full 5 entries to evaluate
        }
        long sumErrors = lastFive.stream()
            .mapToLong(es -> es.getErrorCount() != null ? es.getErrorCount() : 0L)
            .sum();
        long sumTotal = lastFive.stream()
            .mapToLong(es -> es.getTotalMessage() != null ? es.getTotalMessage() : 0L)
            .sum();
        double avgErrorRate = sumTotal > 0 ? (sumErrors * 100.0 / sumTotal) : 0.0;

        logger.info("Evaluating circuit breaker (last5 sums): sumErrors={}, sumTotal={}, computedAvgErrorRate={}, thresholds(errorRate>{}, total>{}), currentFlag={}",
            sumErrors, sumTotal, avgErrorRate, tripErrorRate, minTotal, flag.get());

        Instant now = Instant.now();
        boolean current = flag.get();
        if (!current && avgErrorRate > tripErrorRate && sumTotal > minTotal) {
            logger.warn("Circuit breaker tripped based on last 5 intervals! avgErrorRate={}, sumTotal={}", avgErrorRate, sumTotal);
            flag.set(true);
            lastFlagChange.set(now);
            nextResetCheck.set(now.plus(Duration.ofMinutes(resetIntervalMinutes)));
            insertCircuitBreakerStatus(true, now);
        }
        // Scheduled reset attempts every 15 minutes while tripped
        if (current) {
            Instant scheduled = nextResetCheck.get();
            if (now.isBefore(scheduled)) {
                long secs = Duration.between(now, scheduled).getSeconds();
                logger.debug("Breaker tripped; next reset check in {}s at {}", secs, scheduled);
                return; // skip until scheduled time
            }
            CircuitBreakerStatus lastY = cbRepo.findTop1ByFlagOrderByTimestampDesc("Y");
            if (lastY != null && lastY.getTimestamp() != null) {
                Instant lastTrip = lastY.getTimestamp().toInstant();
                long minutesSinceTrip = Duration.between(lastTrip, now).toMinutes();
                if (minutesSinceTrip < resetIntervalMinutes) {
                    // ensure first attempt aligns with 15 min mark
                    Instant first = lastTrip.plus(Duration.ofMinutes(resetIntervalMinutes));
                    nextResetCheck.set(first);
                    logger.debug("Breaker tripped; only {} minutes since trip. First reset attempt scheduled at {}", minutesSinceTrip, first);
                    return;
                }
                if (avgErrorRate < resetErrorRate && sumTotal > resetMinTotal) {
                    logger.info("Circuit breaker reset after {} minutes: avgErrorRate={}, sumTotal={}", minutesSinceTrip, avgErrorRate, sumTotal);
                    flag.set(false);
                    lastFlagChange.set(now);
                    nextResetCheck.set(Instant.EPOCH);
                    insertCircuitBreakerStatus(false, now);
                } else {
                    Instant nextAttempt = now.plus(Duration.ofMinutes(resetIntervalMinutes));
                    nextResetCheck.set(nextAttempt);
                    logger.debug("Circuit breaker remains tripped (attempt at {}): avgErrorRate={}, sumTotal={} (need <{} and >{}). Next attempt at {}", now, avgErrorRate, sumTotal, resetErrorRate, resetMinTotal, nextAttempt);
                }
            }
        }
    }

    private void insertCircuitBreakerStatus(boolean flagValue, Instant ts) {
    ZoneId ist = ZoneId.of("Asia/Kolkata");
    OffsetDateTime tsIst = OffsetDateTime.ofInstant(ts, ist);
    logger.info("Inserting circuit breaker status (IST): flag={}, timestamp={}", flagValue, tsIst);
    CircuitBreakerStatus cb = new CircuitBreakerStatus();
    cb.setFlag(flagValue ? "Y" : "N");
    cb.setTimestamp(tsIst);
        cbRepo.save(cb);
    logger.debug("CircuitBreakerStatus inserted: {}", cb);
    }
}
