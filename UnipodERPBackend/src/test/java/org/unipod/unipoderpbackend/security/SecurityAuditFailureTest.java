package org.unipod.unipoderpbackend.security;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.unipod.unipoderpbackend.service.submission.WaluubeAuditLog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SecurityAuditFailureTest {
    @Test
    void auditDatabaseFailureDoesNotSuppressUnauthorizedResponse() throws Exception {
        WaluubeAuditLog auditLog = mock(WaluubeAuditLog.class);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(auditLog)
                .recordRequest(anyString(), anyString(), anyString(), anyString(), anyInt());
        AuthenticationEntryPoint handler = new SecurityConfiguration().authenticationEntryPoint(auditLog);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/submissions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new BadCredentialsException("invalid token"));

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
    }

    @Test
    void auditDatabaseFailureDoesNotSuppressForbiddenResponse() throws Exception {
        WaluubeAuditLog auditLog = mock(WaluubeAuditLog.class);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(auditLog)
                .recordRequest(anyString(), anyString(), anyString(), anyString(), anyInt());
        AccessDeniedHandler handler = new SecurityConfiguration().accessDeniedHandler(auditLog);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/submissions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new org.springframework.security.access.AccessDeniedException("denied"));

        assertEquals(403, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
    }
}
