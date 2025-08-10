package com.example.cibil.entity;

import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Table("circuitbreaker_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CircuitBreakerStatus {
    @Id
    @Column("seqnum")
    private Long seqnum; // identity column

    @Column("flag")
    private String flag; // 'Y' or 'N'

    @Column("timestamp")
    private OffsetDateTime timestamp;
}
