package org.unipod.unipoderpbackend.service.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;

    public SmtpEmailSender(JavaMailSender mailSender, String mailHost) {
        if (mailHost == null || mailHost.isBlank()) {
            throw new IllegalStateException("SMTP_HOST must be configured when NOTIFICATION_EMAIL_PROVIDER is smtp.");
        }
        this.mailSender = mailSender;
    }

    @Override
    public void send(SimpleMailMessage message) {
        mailSender.send(message);
    }
}
