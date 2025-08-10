package com.example.cibil.repo;

import com.example.cibil.entity.CircuitBreakerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircuitBreakerRepository extends JpaRepository<CircuitBreakerStatus, Long> {
	CircuitBreakerStatus findTop1ByFlagOrderByTimestampDesc(String flag);
}
 
