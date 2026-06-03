package com.rtrs.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "rtrs.security.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_KEY_LENGTH = 32;

    private String secretKey;
    private long accessTokenExpiryMs = 900000;
    private String issuer = "rtrs-auth-service";
    private String audience = "rtrs-services";

    public void validate() {
        Assert.hasText(secretKey, "rtrs.security.jwt.secret-key must not be blank");
        Assert.isTrue(
                secretKey.length() >= MIN_SECRET_KEY_LENGTH,
                "rtrs.security.jwt.secret-key must be at least " + MIN_SECRET_KEY_LENGTH + " characters (HS256 minimum)"
        );
        Assert.hasText(issuer, "rtrs.security.jwt.issuer must not be blank");
        Assert.hasText(audience, "rtrs.security.jwt.audience must not be blank");
    }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public long getAccessTokenExpiryMs() { return accessTokenExpiryMs; }
    public void setAccessTokenExpiryMs(long accessTokenExpiryMs) { this.accessTokenExpiryMs = accessTokenExpiryMs; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
}