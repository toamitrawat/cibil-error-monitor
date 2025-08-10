package com.example.cibil.entity;

import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

@Table("error_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorStats {
    @Id
    private Long id; // identity column in Oracle

    // Map camelCase -> snake_case columns explicitly
    @Column("start_time")
    private OffsetDateTime startTime;
    @Column("end_time")
    private OffsetDateTime endTime;
    @Column("total_message")
    private Long totalMessage;
    @Column("error_count")
    private Long errorCount;
    @Column("error_rate")
    private Double errorRate;
    @Column("created_time")
    private OffsetDateTime createdTime;
}
