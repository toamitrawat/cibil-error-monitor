package com.example.cibil.repo;

import com.example.cibil.entity.ErrorStats;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;

public interface ErrorStatsRepository extends ListCrudRepository<ErrorStats, Long> {
	// Fetch the most recent 5 rows ordered by endTime descending
	List<ErrorStats> findTop5ByOrderByEndTimeDesc();
}
