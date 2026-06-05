package com.rtrs.tradeingestionservice.api;

import com.rtrs.common.response.ApiResponse;
import com.rtrs.tradeingestionservice.model.TradeRequest;
import com.rtrs.tradeingestionservice.model.TradeResponse;
import com.rtrs.tradeingestionservice.service.TradeIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeIngestionService tradeIngestionService;

    // idempotency + validation yahan se shuru hoti hai
    @PostMapping
    public ResponseEntity<ApiResponse<TradeResponse>> submitTrade(
            @Valid @RequestBody TradeRequest request) {

        log.info("POST /api/v1/trades received. clientOrderRef={}", request.getClientOrderRef());

        TradeResponse response = tradeIngestionService.submitTrade(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response, "Trade submitted successfully"));
    }
}