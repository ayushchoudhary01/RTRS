package com.rtrs.reconciliationservice.api;

import com.rtrs.reconciliationservice.domain.ReconciliationBreak;
import com.rtrs.reconciliationservice.domain.ReconciliationRun;
import com.rtrs.reconciliationservice.enums.BreakStatus;
import com.rtrs.reconciliationservice.exception.ResourceNotFoundException;
import com.rtrs.reconciliationservice.repository.ReconciliationBreakRepository;
import com.rtrs.reconciliationservice.repository.ReconciliationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationBreakRepository breakRepository;

    @GetMapping("/runs")
    public Page<ReconciliationRunResponse> getRuns(
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return runRepository.findAll(pageable)
                .map(ReconciliationRunResponse::from);
    }

    @GetMapping("/runs/{id}")
    public ReconciliationRunResponse getRun(@PathVariable UUID id) {
        ReconciliationRun run = runRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found: " + id));
        return ReconciliationRunResponse.from(run);
    }

    @GetMapping("/breaks")
    public Page<ReconciliationBreakResponse> getBreaks(
            @RequestParam(required = false) BreakStatus status,
            @PageableDefault(size = 20, sort = "detectedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ReconciliationBreak> breaks = (status != null)
                ? breakRepository.findByStatus(status, pageable)
                : breakRepository.findAll(pageable);

        return breaks.map(ReconciliationBreakResponse::from);
    }

    @GetMapping("/breaks/{id}")
    public ReconciliationBreakResponse getBreak(@PathVariable UUID id) {
        ReconciliationBreak brk = breakRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation break not found: " + id));
        return ReconciliationBreakResponse.from(brk);
    }
}
