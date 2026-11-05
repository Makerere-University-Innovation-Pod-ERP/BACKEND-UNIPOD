package org.unipod.unipoderpbackend.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class JwtConfigurationCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("jwt.issuer-uri"))
                && StringUtils.hasText(context.getEnvironment().getProperty("jwt.audience"));
    }
}
