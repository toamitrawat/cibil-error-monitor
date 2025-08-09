package com.example.cibil.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "circuitbreaker_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CircuitBreakerStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seqnum")
    private Long seqnum;

    @Column(name = "flag")
    private String flag; // 'Y' or 'N'

    @Column(name = "timestamp")
    private OffsetDateTime timestamp;
}
