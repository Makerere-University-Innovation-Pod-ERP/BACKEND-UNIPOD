package org.unipod.unipoderpbackend.service.notification;

import org.springframework.mail.SimpleMailMessage;

@FunctionalInterface
public interface EmailSender {
    void send(SimpleMailMessage message);
}
