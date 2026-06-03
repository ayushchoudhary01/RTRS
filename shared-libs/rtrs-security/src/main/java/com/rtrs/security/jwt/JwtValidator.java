package com.rtrs.security.jwt;

import com.rtrs.security.rbac.RtrsRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;


// This implementation uses HS256 by default.
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
    private static final String EXPECTED_TOKEN_TYPE = "access";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtValidator(JwtProperties properties) {
        properties.validate();
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getSecretKey().getBytes(StandardCharsets.UTF_8)
        );
    }

    public AuthenticatedUser validate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token must not be null or blank");
        }
        Claims claims = extractClaims(token);
        validateIssuer(claims);
        validateAudience(claims);
        validateTokenType(claims);
        return buildAuthenticatedUser(claims);
    }

    private Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    private void validateIssuer(Claims claims) {
        String issuer = claims.getIssuer();
        if (issuer == null || !issuer.equals(properties.getIssuer())) {
            throw new InvalidTokenException("Invalid token issuer");
        }
    }

    private void validateAudience(Claims claims) {
        if (claims.getAudience() == null || !claims.getAudience().contains(properties.getAudience())) {
            throw new InvalidTokenException("Invalid token audience");
        }
    }

    private void validateTokenType(Claims claims) {
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!EXPECTED_TOKEN_TYPE.equals(tokenType)) {
            throw new InvalidTokenException("Invalid token type — only access tokens are accepted");
        }
    }

    private AuthenticatedUser buildAuthenticatedUser(Claims claims) {
        String userId = claims.getSubject();
        String username = claims.get(CLAIM_USERNAME, String.class);
        String tokenId = claims.getId();
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        List<RtrsRole> roles = extractRoles(claims);
        return new AuthenticatedUser(userId, username, roles, tokenId, tokenType);
    }

    private List<RtrsRole> extractRoles(Claims claims) {
        Object rawRoles = claims.get(CLAIM_ROLES);
        if (!(rawRoles instanceof List<?>)) {
            throw new InvalidTokenException("Token roles claim is missing or malformed");
        }
        try {
            return ((List<?>) rawRoles).stream()
                    .filter(r -> r instanceof String)
                    .map(r -> RtrsRole.valueOf((String) r))
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("Token contains unrecognised role");
        }
    }
}