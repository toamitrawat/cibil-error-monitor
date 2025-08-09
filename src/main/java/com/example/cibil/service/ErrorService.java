package com.example.cibil.service;

import com.example.cibil.entity.CircuitBreakerStatus;
import com.example.cibil.entity.ErrorStats;
import com.example.cibil.repo.CircuitBreakerRepository;
import com.example.cibil.repo.ErrorStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ErrorService {

    private final CircuitBreakerRepository cbRepo;
    private final ErrorStatsRepository statsRepo;

    private static final Logger logger = LogManager.getLogger(ErrorService.class);

    // track in-memory state for the flag and last change time
    private final AtomicReference<Boolean> flag = new AtomicReference<>(false);
    private final AtomicReference<Instant> lastFlagChange = new AtomicReference<>(Instant.EPOCH);

    @Value("${app.circuit.threshold.error-rate:50.0}")
    private double tripErrorRate;

    @Value("${app.circuit.threshold.min-total:10}")
    private long minTotal;

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
    public void evaluateCircuitBreaker(double errorRate, long total) {
    logger.info("Evaluating circuit breaker: errorRate={}, total={}, flag={}", errorRate, total, flag.get());
        Instant now = Instant.now();

        boolean current = flag.get();
        if (!current && errorRate > tripErrorRate && total > minTotal) {
            logger.warn("Circuit breaker tripped! errorRate={}, total={}, configuredTripRate={}, minTotal={}", errorRate, total, tripErrorRate, minTotal);
            // trip circuit
            flag.set(true);
            lastFlagChange.set(now);
            insertCircuitBreakerStatus(true, now);
        /*
        } else if (current && errorRate <= 5.0) {
            // only reset if 15 minutes have passed since tripped
            Instant since = lastFlagChange.get();
            if (ChronoUnit.MINUTES.between(since, now) >= 15) {
                flag.set(false);
                lastFlagChange.set(now);
                insertCircuitBreakerStatus(false, now);
            }
        */
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
