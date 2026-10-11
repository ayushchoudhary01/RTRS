package com.rtrs.riskengineservice.api;

import com.rtrs.common.enums.ErrorCode;
import com.rtrs.common.exception.DomainException;
import com.rtrs.riskengineservice.domain.RiskEvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// First REST surface on this service — it is otherwise a pure Kafka consumer
// Read-only, so the evaluate-and-publish path is untouched
@RestController
@RequestMapping("/api/v1/risk/evaluations")
@RequiredArgsConstructor
public class RiskQueryController {

    private final RiskEvaluationRepository riskEvaluationRepository;

    @GetMapping("/{tradeId}")
    @Transactional(readOnly = true)
    public ResponseEntity<RiskEvaluationResponse> getEvaluation(@PathVariable UUID tradeId) {
        return riskEvaluationRepository.findByTradeId(tradeId)
                .map(RiskEvaluationResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new DomainException(
                        "No risk evaluation found for tradeId: " + tradeId,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND));
    }
}
