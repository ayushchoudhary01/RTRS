package com.rtrs.common.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {

    private String accountId;
    private String ownerId;
    private String accountType;
    private BigDecimal balance;
    private String currency;
    private boolean active;
    private Instant createdAt;
}