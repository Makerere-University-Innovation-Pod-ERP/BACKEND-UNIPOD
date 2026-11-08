package org.unipod.unipoderpbackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unipod.unipoderpbackend.service.submission.CaseFileEnrichmentProvider;
import org.unipod.unipoderpbackend.service.submission.UnconfiguredCaseFileEnrichmentProvider;

@Configuration(proxyBeanMethods = false)
public class CaseFileIntegrationConfiguration {
    @Bean
    @ConditionalOnMissingBean(CaseFileEnrichmentProvider.class)
    CaseFileEnrichmentProvider unconfiguredCaseFileEnrichmentProvider() {
        return new UnconfiguredCaseFileEnrichmentProvider();
    }
}
