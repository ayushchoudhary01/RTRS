package com.rtrs.settlementservice.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementRunRepository extends JpaRepository<SettlementRun, UUID> {
}
