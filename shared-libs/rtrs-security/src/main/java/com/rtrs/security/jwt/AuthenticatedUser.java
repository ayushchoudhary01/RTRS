package com.rtrs.security.jwt;

import com.rtrs.security.rbac.RtrsRole;

import java.util.List;

public class AuthenticatedUser {

    private final String userId;
    private final String username;
    private final List<RtrsRole> roles;
    private final String tokenId;
    private final String tokenType;

    public AuthenticatedUser(
            String userId,
            String username,
            List<RtrsRole> roles,
            String tokenId,
            String tokenType) {
        this.userId = userId;
        this.username = username;
        this.roles = List.copyOf(roles);
        this.tokenId = tokenId;
        this.tokenType = tokenType;
    }

    public boolean hasRole(RtrsRole role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(RtrsRole... requiredRoles) {
        for (RtrsRole role : requiredRoles) {
            if (roles.contains(role)) return true;
        }
        return false;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public List<RtrsRole> getRoles() { return roles; }
    public String getTokenId() { return tokenId; }
    public String getTokenType() { return tokenType; }
}