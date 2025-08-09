package com.example.cibil.service;

import com.example.cibil.entity.CircuitBreakerStatus;
import com.example.cibil.entity.ErrorStats;
import com.example.cibil.repo.CircuitBreakerRepository;
import com.example.cibil.repo.ErrorStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ErrorService {

    private final CircuitBreakerRepository cbRepo;
    private final ErrorStatsRepository statsRepo;

    // track in-memory state for the flag and last change time
    private final AtomicReference<Boolean> flag = new AtomicReference<>(false);
    private final AtomicReference<Instant> lastFlagChange = new AtomicReference<>(Instant.EPOCH);

    public ErrorService(CircuitBreakerRepository cbRepo, ErrorStatsRepository statsRepo) {
        this.cbRepo = cbRepo;
        this.statsRepo = statsRepo;
    }

    @Transactional
    public void saveStats(Instant start, Instant end, long total, long errors, double rate) {
        ErrorStats stats = new ErrorStats();
        stats.setStartTime(start);
        stats.setEndTime(end);
        stats.setTotalMessage(total);
        stats.setErrorCount(errors);
        stats.setErrorRate(rate);
        stats.setCreatedTime(Instant.now());
        statsRepo.save(stats);
    }

    @Transactional
    public void evaluateCircuitBreaker(double errorRate) {
        Instant now = Instant.now();

        boolean current = flag.get();
        if (!current && errorRate > 50.0) {
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
        CircuitBreakerStatus cb = new CircuitBreakerStatus();
        cb.setFlag(flagValue ? "Y" : "N");
        cb.setTimestamp(ts);
        cbRepo.save(cb);
    }
}
