package com.rtrs.settlementservice.settlement;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DvpResult {
    private final boolean passed;
    private final String failureReason;

    public static DvpResult pass() {
        return new DvpResult(true, null);
    }

    public static DvpResult fail(String reason) {
        return new DvpResult(false, reason);
    }
}
