package org.unipod.unipoderpbackend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks API use until a user with a temporary password sets a permanent one.
 * The flag is carried in the access token, so the check is stateless and
 * applies to every request that presents an affected token.
 */
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof Jwt jwt
                && Boolean.TRUE.equals(jwt.getClaimAsBoolean("mustChangePassword"))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Change your temporary password before using the API."
            );
        }
        return true;
    }
}
