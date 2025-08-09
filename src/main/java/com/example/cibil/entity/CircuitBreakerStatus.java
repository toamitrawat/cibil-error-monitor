package com.example.cibil.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "circuitbreaker_status")
public class CircuitBreakerStatus {
    @Id
    @Column(name = "seqnum")
    private Long seqnum;

    @Column(name = "flag")
    private String flag; // 'Y' or 'N'

    @Column(name = "timestamp")
    private Instant timestamp;

    public Long getSeqnum() { return seqnum; }
    public void setSeqnum(Long seqnum) { this.seqnum = seqnum; }
    public String getFlag() { return flag; }
    public void setFlag(String flag) { this.flag = flag; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
