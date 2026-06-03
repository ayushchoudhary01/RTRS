package com.rtrs.security.rbac;

import com.rtrs.security.jwt.AuthenticatedUser;
import com.rtrs.security.jwt.InvalidTokenException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Aspect
@Component
public class RoleEnforcementAspect {

    private static final String AUTHENTICATED_USER_ATTR = "authenticatedUser";

    @Before("@annotation(requiresRole)")
    public void enforceRole(JoinPoint joinPoint, RequiresRole requiresRole) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new InvalidTokenException("No request context found");
        }

        HttpServletRequest request = attributes.getRequest();
        AuthenticatedUser user = (AuthenticatedUser) request.getAttribute(AUTHENTICATED_USER_ATTR);

        if (user == null) {
            throw new InvalidTokenException("No authenticated user in request context");
        }

        boolean hasRequiredRole = Arrays.stream(requiresRole.value())
                .anyMatch(user::hasRole);

        if (!hasRequiredRole) {
            throw new AccessDeniedException(
                    "User " + user.getUsername() + " does not have required role"
            );
        }
    }
}