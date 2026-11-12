package org.unipod.unipoderpbackend.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.unipod.unipoderpbackend.service.submission.WaluubeAuditLog;

import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);
    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> decoderProvider,
            JwtAuthenticationConverter authenticationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=()")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/auth/login", "/auth/set-password").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        JwtDecoder decoder = decoderProvider.getIfAvailable();
        if (decoder != null) {
            http.oauth2ResourceServer(resourceServer -> resourceServer
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(authenticationConverter)));
        }
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            @Value("${jwt.roles-claim:roles}") String rolesClaim,
            @Value("${jwt.subject-claim:sub}") String subjectClaim
    ) {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(rolesClaim);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName(subjectClaim);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(WaluubeAuditLog auditLog) {
        return (request, response, exception) -> {
            recordSecurityAudit(auditLog, request,
                    currentActor(),
                    HttpStatus.UNAUTHORIZED.value()
            );
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,"detail":"A valid bearer token is required."}
                    """.trim());
        };
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(WaluubeAuditLog auditLog) {
        return (request, response, exception) -> {
            recordSecurityAudit(auditLog, request,
                    currentActor(),
                    HttpStatus.FORBIDDEN.value()
            );
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Forbidden","status":403,"detail":"You do not have permission to perform this action."}
                    """.trim());
        };
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                ? "anonymous"
                : authentication.getName();
    }

    private static void recordSecurityAudit(
            WaluubeAuditLog auditLog,
            jakarta.servlet.http.HttpServletRequest request,
            String actor,
            int status
    ) {
        try {
            auditLog.recordRequest(
                    actor,
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getContextPath(),
                    status
            );
        } catch (DataAccessException exception) {
            log.warn("Unable to persist Waluube security audit event for {} {} (status {})",
                    request.getMethod(), request.getRequestURI(), status, exception);
        }
    }
}
