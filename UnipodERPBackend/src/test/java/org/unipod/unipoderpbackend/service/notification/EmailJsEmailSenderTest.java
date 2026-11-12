package org.unipod.unipoderpbackend.service.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EmailJsEmailSenderTest {
    private static final String SEND_URL = "https://api.emailjs.test/api/v1.0/email/send";

    private MockRestServiceServer server;
    private EmailJsEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new EmailJsEmailSender(
                builder,
                "https://api.emailjs.test",
                "service_test",
                "template_test",
                "public_test",
                "private_test"
        );
    }

    @Test
    void sendsTheEmailThroughTheEmailJsApi() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.service_id").value("service_test"))
                .andExpect(jsonPath("$.template_id").value("template_test"))
                .andExpect(jsonPath("$.user_id").value("public_test"))
                .andExpect(jsonPath("$.accessToken").value("private_test"))
                .andExpect(jsonPath("$.template_params.to_email").value("person@example.test"))
                .andExpect(jsonPath("$.template_params.subject").value("Reset your UNIPOD account password"))
                .andExpect(jsonPath("$.template_params.message").value("Use this link."))
                .andRespond(withSuccess("OK", MediaType.TEXT_PLAIN));

        sender.send(message("person@example.test"));

        server.verify();
    }

    @Test
    void aRefusalBecomesAMailExceptionSoTheOutboxRetries() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("API calls are disabled for non-browser applications"));

        assertThrows(MailSendException.class, () -> sender.send(message("person@example.test")));
    }

    @Test
    void missingKeysStopTheAppFromStarting() {
        assertThrows(IllegalStateException.class, () -> new EmailJsEmailSender(
                RestClient.builder(), "https://api.emailjs.test", "service_test", "template_test", "public_test", " "
        ));
    }

    private static SimpleMailMessage message(String to) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@example.test");
        message.setTo(to);
        message.setSubject("Reset your UNIPOD account password");
        message.setText("Use this link.");
        return message;
    }
}
