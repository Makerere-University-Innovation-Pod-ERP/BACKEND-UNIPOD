package org.unipod.unipoderpbackend.service.partner;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Registered here so the shared application class stays untouched. Scheduling is enabled here too
// because the only other switch is conditional on email delivery, and the MOU job must run without it.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PartnerMouProperties.class)
@EnableScheduling
class PartnerConfiguration {
}
