package org.unipod.unipoderpbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.unipod.unipoderpbackend.service.submission.WaluubeAuditLog;

@Configuration(proxyBeanMethods = false)
public class WaluubeAuditConfiguration implements WebMvcConfigurer {
    private final WaluubeAuditLog auditLog;

    public WaluubeAuditConfiguration(WaluubeAuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new WaluubeAuditInterceptor(auditLog))
                .addPathPatterns(WaluubeAuditLog.PATH_PATTERNS.toArray(String[]::new));
    }
}
