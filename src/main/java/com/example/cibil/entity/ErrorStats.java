package com.example.cibil.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "error_stats")
public class ErrorStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant startTime;
    private Instant endTime;
    private Long totalMessage;
    private Long errorCount;
    private Double errorRate;
    private Instant createdTime;

    public Long getId() { return id; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public Long getTotalMessage() { return totalMessage; }
    public void setTotalMessage(Long totalMessage) { this.totalMessage = totalMessage; }
    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }
    public Double getErrorRate() { return errorRate; }
    public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }
    public Instant getCreatedTime() { return createdTime; }
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }
}
