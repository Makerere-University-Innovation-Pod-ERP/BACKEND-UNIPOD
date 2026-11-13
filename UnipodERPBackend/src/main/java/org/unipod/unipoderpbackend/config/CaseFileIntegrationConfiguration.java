package org.unipod.unipoderpbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.service.review.ReviewPanelQueryService;
import org.unipod.unipoderpbackend.service.submission.CaseFileEnrichmentProvider;
import org.unipod.unipoderpbackend.service.submission.DefaultCaseFileEnrichmentProvider;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class CaseFileIntegrationConfiguration {
    @Bean
    CaseFileEnrichmentProvider caseFileEnrichmentProvider(
            ReviewPanelQueryService panels,
            AuditEntryRepository auditEntries,
            ObjectMapper objectMapper
    ) {
        return new DefaultCaseFileEnrichmentProvider(panels, auditEntries, objectMapper);
    }
}
