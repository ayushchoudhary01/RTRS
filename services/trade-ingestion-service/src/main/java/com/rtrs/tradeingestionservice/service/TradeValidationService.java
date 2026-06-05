package com.rtrs.tradeingestionservice.service;

import com.rtrs.common.enums.TradeType;
import com.rtrs.common.exception.DomainException;
import com.rtrs.common.enums.ErrorCode;
import com.rtrs.tradeingestionservice.model.TradeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;

@Slf4j
@Service
public class TradeValidationService {

    // Supported currencies, production mein database se aayega
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "JPY", "INR", "CHF", "AUD", "CAD"
    );

    // Maximum trade value (risk engine se pehle basic sanity check)
    private static final BigDecimal MAX_TRADE_VALUE = new BigDecimal("100000000");

    public void validate(TradeRequest request) {
        validateCurrency(request.getCurrency());
        validateTradeValue(request.getQuantity(), request.getLimitPrice());
        validateShortSell(request.getTradeType(), request.getQuantity());
        log.debug("Trade validation passed. clientOrderRef={}", request.getClientOrderRef());
    }

    // Currency ISO 4217 valid hai ya nhi
    private void validateCurrency(String currency) {
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new DomainException(
                    "Unsupported currency: " + currency,
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(
                    "Invalid ISO 4217 currency code: " + currency,
                    ErrorCode.VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    // Total trade value 100M se upar nhi honi chahiye (also basic sanity check)
    private void validateTradeValue(BigDecimal quantity, BigDecimal limitPrice) {
        BigDecimal totalValue = quantity.multiply(limitPrice);
        if (totalValue.compareTo(MAX_TRADE_VALUE) > 0) {
            throw new DomainException(
                    "Trade value exceeds maximum allowed limit of " + MAX_TRADE_VALUE,
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    // SHORT sell ke liye quantity positive honi chahiye — negative short nhi chalegi
    private void validateShortSell(TradeType tradeType, BigDecimal quantity) {
        if (tradeType == TradeType.SHORT && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException(
                    "Short sell quantity must be positive",
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }
}