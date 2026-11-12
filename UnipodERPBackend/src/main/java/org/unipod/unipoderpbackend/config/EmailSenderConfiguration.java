package org.unipod.unipoderpbackend.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;
import org.unipod.unipoderpbackend.service.notification.EmailJsEmailSender;
import org.unipod.unipoderpbackend.service.notification.EmailSender;
import org.unipod.unipoderpbackend.service.notification.SmtpEmailSender;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "true")
public class EmailSenderConfiguration {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    @ConditionalOnProperty(name = "notifications.email.provider", havingValue = "smtp", matchIfMissing = true)
    EmailSender smtpEmailSender(JavaMailSender mailSender, @Value("${spring.mail.host:}") String mailHost) {
        return new SmtpEmailSender(mailSender, mailHost);
    }

    @Bean
    @ConditionalOnProperty(name = "notifications.email.provider", havingValue = "emailjs")
    EmailSender emailJsEmailSender(
            @Value("${notifications.email.emailjs.base-url:https://api.emailjs.com}") String baseUrl,
            @Value("${notifications.email.emailjs.service-id:}") String serviceId,
            @Value("${notifications.email.emailjs.template-id:}") String templateId,
            @Value("${notifications.email.emailjs.public-key:}") String publicKey,
            @Value("${notifications.email.emailjs.private-key:}") String privateKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return new EmailJsEmailSender(
                RestClient.builder().requestFactory(requestFactory),
                baseUrl,
                serviceId,
                templateId,
                publicKey,
                privateKey
        );
    }
}
