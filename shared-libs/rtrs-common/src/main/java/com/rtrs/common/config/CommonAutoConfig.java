package com.rtrs.common.config;

import com.rtrs.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

// rtrs-common lives in com.rtrs.common, a sibling of every service's base package,
// so @SpringBootApplication component scanning never reaches it
// without this, GlobalExceptionHandler was never registered and DomainException
// fell through to a generic 500 instead of the status it carries
@AutoConfiguration
public class CommonAutoConfig {

    // Deliberately not named globalExceptionHandler — reconciliation-service and
    // settlement-service component-scan their own classes under that bean name,
    // and a clash would fail startup with BeanDefinitionOverrideException
    @Bean
    public GlobalExceptionHandler commonGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
