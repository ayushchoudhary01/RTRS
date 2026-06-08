package com.rtrs.tradeprocessorservice.dlq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DlqAlertPublisher {

    // DLQ alert — production me yahan PagerDuty ya Slack integration hogi
    public void alert(String key, String topic, String payload) {
        log.error("ALERT: Unprocessable message in DLQ. topic={}, key={}, payload={}",
                topic, key, payload);
    }
}
