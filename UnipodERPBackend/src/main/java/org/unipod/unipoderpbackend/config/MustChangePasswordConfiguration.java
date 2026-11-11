package org.unipod.unipoderpbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.unipod.unipoderpbackend.security.MustChangePasswordInterceptor;

@Configuration(proxyBeanMethods = false)
public class MustChangePasswordConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MustChangePasswordInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/set-password",
                        "/auth/change-password",
                        "/actuator/health"
                );
    }
}
