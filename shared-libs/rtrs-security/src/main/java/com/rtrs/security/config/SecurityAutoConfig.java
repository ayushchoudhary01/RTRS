package com.rtrs.security.config;

import com.rtrs.security.jwt.JwtProperties;
import com.rtrs.security.jwt.JwtValidator;
import com.rtrs.security.rbac.RoleEnforcementAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@EnableAspectJAutoProxy
public class SecurityAutoConfig {

    @Bean
    public JwtValidator jwtValidator(JwtProperties properties) {
        return new JwtValidator(properties);
    }
    @Bean
    public RoleEnforcementAspect roleEnforcementAspect() {
        return new RoleEnforcementAspect();
    }
}