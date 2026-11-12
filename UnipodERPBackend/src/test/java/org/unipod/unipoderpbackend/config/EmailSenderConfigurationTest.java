package org.unipod.unipoderpbackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.unipod.unipoderpbackend.service.notification.EmailJsEmailSender;
import org.unipod.unipoderpbackend.service.notification.EmailSender;
import org.unipod.unipoderpbackend.service.notification.SmtpEmailSender;

class EmailSenderConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EmailSenderConfiguration.class)
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class));

    @Test
    void noSenderWhenEmailIsSwitchedOff() {
        runner.run(context -> assertThat(context).doesNotHaveBean(EmailSender.class));
    }

    @Test
    void smtpIsTheDefault() {
        runner.withPropertyValues("notifications.email.enabled=true", "spring.mail.host=smtp.example.test")
                .run(context -> assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class));
    }

    @Test
    void emailJsWhenTheProviderSaysSo() {
        runner.withPropertyValues(
                        "notifications.email.enabled=true",
                        "notifications.email.provider=emailjs",
                        "notifications.email.emailjs.service-id=service_test",
                        "notifications.email.emailjs.template-id=template_test",
                        "notifications.email.emailjs.public-key=public_test",
                        "notifications.email.emailjs.private-key=private_test"
                )
                .run(context -> assertThat(context.getBean(EmailSender.class)).isInstanceOf(EmailJsEmailSender.class));
    }

    @Test
    void emailJsWithoutKeysFailsAtStartup() {
        runner.withPropertyValues("notifications.email.enabled=true", "notifications.email.provider=emailjs")
                .run(context -> assertThat(context).hasFailed());
    }
}
