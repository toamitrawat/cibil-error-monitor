package com.example.cibil.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "error_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
}
