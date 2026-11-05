package org.unipod.unipoderpbackend.submissions;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "true")
public class NotificationSchedulingConfiguration {
}
