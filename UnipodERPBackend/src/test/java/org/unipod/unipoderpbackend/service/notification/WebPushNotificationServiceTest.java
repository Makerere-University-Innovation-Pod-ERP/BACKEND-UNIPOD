package org.unipod.unipoderpbackend.service.notification;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.notification.PushSubscriptionRequest;
import org.unipod.unipoderpbackend.model.notification.WebPushSubscription;
import org.unipod.unipoderpbackend.repository.notification.WebPushSubscriptionRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebPushNotificationServiceTest {
    private static final String VAPID_PUBLIC_KEY =
            "BGgL7I82SAQM78oyGwaJdrQFhVfZqL9h4Y18BLtgJQ-9pSGXwxqAWQudqmcv41RcWgk1ssUeItv4-8khxbhYveM=";
    private static final String VAPID_PRIVATE_KEY = "ANlfcVVFB4JiMYcI74_h9h04QZ1Ks96AyEa1yrMgDwn3";
    private final WebPushSubscriptionRepository subscriptions = mock(WebPushSubscriptionRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.system(ZoneId.of("Africa/Kampala"));

    @Test
    void pushIsExplicitlyDisabledUntilVapidCredentialsAreConfigured() {
        WebPushNotificationService service = service("", "", "");

        assertFalse(service.configuration().enabled());
        assertThrows(ResponseStatusException.class, () -> service.subscribe(
                "person@example.test",
                new PushSubscriptionRequest(
                        "https://fcm.googleapis.com/fcm/send/subscription",
                        "p256dh-key",
                        "auth-key"
                )
        ));
    }

    @Test
    void onlyKnownSecurePushServiceEndpointsAreAllowed() {
        assertTrue(WebPushNotificationService.isAllowedEndpoint(
                "https://fcm.googleapis.com/fcm/send/subscription"));
        assertTrue(WebPushNotificationService.isAllowedEndpoint(
                "https://updates.push.services.mozilla.com/wpush/v2/subscription"));
        assertFalse(WebPushNotificationService.isAllowedEndpoint(
                "http://fcm.googleapis.com/fcm/send/subscription"));
        assertFalse(WebPushNotificationService.isAllowedEndpoint(
                "https://localhost/fcm/send/subscription"));
        assertFalse(WebPushNotificationService.isAllowedEndpoint(
                "https://fcm.googleapis.com:8443/fcm/send/subscription"));
    }

    @Test
    void subscriptionIsScopedToTheAuthenticatedAccount() {
        String endpoint = "https://fcm.googleapis.com/fcm/send/subscription";
        WebPushSubscription existing = new WebPushSubscription(
                UUID.randomUUID(),
                "other@example.test",
                endpoint,
                "existing-public-key",
                "existing-auth-key",
                Instant.now()
        );
        when(subscriptions.findByEndpoint(endpoint)).thenReturn(Optional.of(existing));
        WebPushNotificationService service = service(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, "mailto:tests@example.test");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.subscribe(
                "person@example.test",
                new PushSubscriptionRequest(endpoint, "public-key", "auth-key")
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(subscriptions, never()).save(any());
    }

    private WebPushNotificationService service(String publicKey, String privateKey, String subject) {
        return new WebPushNotificationService(subscriptions, objectMapper, clock, publicKey, privateKey, subject);
    }
}
