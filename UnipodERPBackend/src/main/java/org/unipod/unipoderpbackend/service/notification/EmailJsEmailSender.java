package org.unipod.unipoderpbackend.service.notification;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class EmailJsEmailSender implements EmailSender {
    private static final String SEND_PATH = "/api/v1.0/email/send";

    private final RestClient client;
    private final String serviceId;
    private final String templateId;
    private final String publicKey;
    private final String privateKey;

    public EmailJsEmailSender(
            RestClient.Builder builder,
            String baseUrl,
            String serviceId,
            String templateId,
            String publicKey,
            String privateKey
    ) {
        if (isBlank(serviceId) || isBlank(templateId) || isBlank(publicKey) || isBlank(privateKey)) {
            throw new IllegalStateException(
                    "EMAILJS_SERVICE_ID, EMAILJS_TEMPLATE_ID, EMAILJS_PUBLIC_KEY and EMAILJS_PRIVATE_KEY "
                            + "must be configured when NOTIFICATION_EMAIL_PROVIDER is emailjs."
            );
        }
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceId = serviceId;
        this.templateId = templateId;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    @Override
    public void send(SimpleMailMessage message) {
        String[] to = message.getTo();
        if (to == null || to.length == 0) {
            throw new MailSendException("The email has no recipient.");
        }
        Map<String, Object> body = Map.of(
                "service_id", serviceId,
                "template_id", templateId,
                "user_id", publicKey,
                "accessToken", privateKey,
                "template_params", Map.of(
                        "to_email", to[0],
                        "subject", valueOrEmpty(message.getSubject()),
                        "message", valueOrEmpty(message.getText())
                )
        );
        try {
            client.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new MailSendException("EmailJS refused the email with status " + exception.getStatusCode().value() + ".");
        } catch (RestClientException exception) {
            throw new MailSendException("EmailJS could not be reached.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
