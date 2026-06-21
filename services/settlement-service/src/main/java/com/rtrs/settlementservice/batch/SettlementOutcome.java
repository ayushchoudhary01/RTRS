package com.rtrs.settlementservice.batch;

import java.util.UUID;

public record SettlementOutcome(UUID obligationId, boolean success) {
}
