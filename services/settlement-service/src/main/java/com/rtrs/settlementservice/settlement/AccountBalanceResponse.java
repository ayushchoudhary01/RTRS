package com.rtrs.settlementservice.settlement;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AccountBalanceResponse {
    private UUID accountId;
    private BigDecimal balance;
    private String currency;
}
