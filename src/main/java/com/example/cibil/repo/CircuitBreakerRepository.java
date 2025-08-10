package com.example.cibil.repo;

import com.example.cibil.entity.CircuitBreakerStatus;
import org.springframework.data.repository.ListCrudRepository;

public interface CircuitBreakerRepository extends ListCrudRepository<CircuitBreakerStatus, Long> {
	CircuitBreakerStatus findTop1ByFlagOrderByTimestampDesc(String flag);
}
 
