package com.rtrs.tradeprocessorservice.api;

import com.rtrs.common.enums.ErrorCode;
import com.rtrs.common.exception.DomainException;
import com.rtrs.tradeprocessorservice.choreography.ApprovalStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Read-only view of the choreography state
// findByTradeId is used rather than the ForUpdate variant — this must never take
// the pessimistic lock that serializes the risk and AML consumer threads
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalQueryController {

    private final ApprovalStateRepository approvalStateRepository;

    @GetMapping("/{tradeId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApprovalStateResponse> getApprovalState(@PathVariable UUID tradeId) {
        return approvalStateRepository.findByTradeId(tradeId)
                .map(ApprovalStateResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new DomainException(
                        "No approval state found for tradeId: " + tradeId,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        HttpStatus.NOT_FOUND));
    }
}
