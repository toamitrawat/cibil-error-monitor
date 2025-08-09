package com.example.cibil.repo;

import com.example.cibil.entity.ErrorStats;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ErrorStatsRepository extends JpaRepository<ErrorStats, Long> {
	// Fetch the most recent 5 rows ordered by endTime descending
	List<ErrorStats> findTop5ByOrderByEndTimeDesc();
}
