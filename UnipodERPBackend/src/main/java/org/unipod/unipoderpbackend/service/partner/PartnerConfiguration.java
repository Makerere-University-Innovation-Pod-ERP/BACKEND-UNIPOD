package org.unipod.unipoderpbackend.service.partner;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Registered here so the shared application class stays untouched.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PartnerMouProperties.class)
class PartnerConfiguration {
}
