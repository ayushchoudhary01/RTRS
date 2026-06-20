package com.rtrs.reconciliationservice.repository;

import com.rtrs.reconciliationservice.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
}
