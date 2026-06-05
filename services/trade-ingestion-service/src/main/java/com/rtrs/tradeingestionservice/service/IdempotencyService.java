package com.rtrs.tradeingestionservice.service;

import com.rtrs.common.exception.IdempotencyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:trade:";

    private final StringRedisTemplate redisTemplate;

    @Value("${rtrs.idempotency.ttl-hours:24}")
    private long ttlHours;

    // Redis mein check kro, agar key exist krti hai toh duplicate request hai
    public void checkAndStore(String clientOrderRef, String tradeId) {
        String key = KEY_PREFIX + clientOrderRef;

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(key, tradeId, Duration.ofHours(ttlHours));

        if (Boolean.FALSE.equals(isNew)) {
            String existingTradeId = redisTemplate.opsForValue().get(key);
            log.warn("Duplicate trade request detected. clientOrderRef={}, existingTradeId={}",
                    clientOrderRef, existingTradeId);
            throw new IdempotencyException(clientOrderRef);
        }

        log.debug("Idempotency key stored. clientOrderRef={}, tradeId={}", clientOrderRef, tradeId);
    }

    // Key delete nhi krte — 24 hours tak rkho taki retry pe duplicate na bane
    public boolean exists(String clientOrderRef) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + clientOrderRef));
    }
}