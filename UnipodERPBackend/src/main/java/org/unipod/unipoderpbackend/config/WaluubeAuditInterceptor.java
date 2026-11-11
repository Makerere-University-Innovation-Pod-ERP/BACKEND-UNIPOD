package org.unipod.unipoderpbackend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.unipod.unipoderpbackend.service.submission.WaluubeAuditLog;

public class WaluubeAuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(WaluubeAuditInterceptor.class);
    private static final String ANONYMOUS_ACTOR = "anonymous";

    private final WaluubeAuditLog auditLog;

    public WaluubeAuditInterceptor(WaluubeAuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                ? ANONYMOUS_ACTOR
                : authentication.getName();
        try {
            auditLog.recordRequest(
                    actor,
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getContextPath(),
                    response.getStatus()
            );
        } catch (DataAccessException auditException) {
            log.warn("Unable to persist Waluube request audit event for {} {}", request.getMethod(),
                    request.getRequestURI(), auditException);
        }
    }
}
