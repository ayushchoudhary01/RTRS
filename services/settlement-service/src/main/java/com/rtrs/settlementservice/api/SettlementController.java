package com.rtrs.settlementservice.api;

import com.rtrs.settlementservice.instruction.SettlementInstruction;
import com.rtrs.settlementservice.instruction.SettlementInstructionRepository;
import com.rtrs.settlementservice.run.SettlementRun;
import com.rtrs.settlementservice.run.SettlementRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.rtrs.settlementservice.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementRunRepository settlementRunRepository;
    private final SettlementInstructionRepository instructionRepository;

    @GetMapping("/runs")
    public ResponseEntity<Page<SettlementRun>> getRuns(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(settlementRunRepository.findAll(pageable));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<SettlementRun> getRun(@PathVariable UUID id) {
        return settlementRunRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementRun not found: " + id));
    }

    @GetMapping("/instructions/{tradeId}")
    public ResponseEntity<SettlementInstruction> getInstruction(@PathVariable UUID tradeId) {
        return instructionRepository.findByTradeId(tradeId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementInstruction not found for tradeId: " + tradeId));
    }
}
