package org.unipod.unipoderpbackend.service.notification;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.notification.PushSubscriptionRequest;
import org.unipod.unipoderpbackend.model.notification.NotificationCreatedEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.notification.WebPushSubscription;
import org.unipod.unipoderpbackend.repository.notification.WebPushSubscriptionRepository;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebPushNotificationServiceTest {
    private static final String VAPID_PUBLIC_KEY =
            "BGgL7I82SAQM78oyGwaJdrQFhVfZqL9h4Y18BLtgJQ-9pSGXwxqAWQudqmcv41RcWgk1ssUeItv4-8khxbhYveM=";
    private static final String VAPID_PRIVATE_KEY = "ANlfcVVFB4JiMYcI74_h9h04QZ1Ks96AyEa1yrMgDwn3";
    private static final String AUTH_SECRET = "AAECAwQFBgcICQoLDA0ODw";
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
                VAPID_PUBLIC_KEY,
                AUTH_SECRET,
                Instant.now()
        );
        when(subscriptions.findByEndpoint(endpoint)).thenReturn(Optional.of(existing));
        WebPushNotificationService service = service(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, "mailto:tests@example.test");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.subscribe(
                "person@example.test",
                new PushSubscriptionRequest(endpoint, VAPID_PUBLIC_KEY, AUTH_SECRET)
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(subscriptions, never()).save(any());
    }

    @Test
    void subscriptionRejectsMalformedEncryptionKeysBeforeSaving() {
        WebPushNotificationService service = service(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, "mailto:tests@example.test");
        String endpoint = "https://fcm.googleapis.com/fcm/send/subscription";

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.subscribe(
                "person@example.test",
                new PushSubscriptionRequest(endpoint, "not-a-public-key", AUTH_SECRET)
        )).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.subscribe(
                "person@example.test",
                new PushSubscriptionRequest(endpoint, VAPID_PUBLIC_KEY, "not-an-auth-secret")
        )).getStatusCode());

        verify(subscriptions, never()).save(any());
    }

    @Test
    void payloadFitsEncryptedByteLimitAndPreservesShortAndMultibyteBodies() throws Exception {
        WebPushNotificationService service = service(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, "mailto:tests@example.test");
        WebPushSubscription subscription = new WebPushSubscription(
                UUID.randomUUID(),
                "person@example.test",
                "https://fcm.googleapis.com/fcm/send/subscription",
                VAPID_PUBLIC_KEY,
                AUTH_SECRET,
                Instant.now()
        );
        NotificationCreatedEvent shortEvent = new NotificationCreatedEvent(
                UUID.randomUUID(), "person@example.test", NotificationType.BRIEF_SUBMITTED,
                "A title", "Short ✓", "submission", UUID.randomUUID()
        );

        String shortPayload = service.payloadFor(shortEvent, subscription);
        assertEquals("Short ✓", objectMapper.readTree(shortPayload).get("body").asText());

        String repeatedEmoji = "😀".repeat(3000);
        NotificationCreatedEvent largeEvent = new NotificationCreatedEvent(
                UUID.randomUUID(), "person@example.test", NotificationType.BRIEF_SUBMITTED,
                "A title", repeatedEmoji, "submission", UUID.randomUUID()
        );
        String largePayload = service.payloadFor(largeEvent, subscription);
        String shortenedBody = objectMapper.readTree(largePayload).get("body").asText();
        Notification notification = new Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                largePayload.getBytes(StandardCharsets.UTF_8),
                86_400
        );
        long encryptedLength = new PushService(VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, "mailto:tests@example.test")
                .preparePost(notification, Encoding.AES128GCM)
                .getEntity()
                .getContentLength();

        assertFalse(shortenedBody.isEmpty());
        assertTrue(repeatedEmoji.startsWith(shortenedBody));
        assertFalse(largePayload.contains("\uFFFD"));
        assertEquals("A title", objectMapper.readTree(largePayload).get("title").asText());
        assertTrue(encryptedLength <= 4096);
    }

    @Test
    void requestIsCancelledWhenItsBoundedWaitTimesOut() throws Exception {
        @SuppressWarnings("unchecked")
        Future<org.apache.http.HttpResponse> request = mock(Future.class);
        when(request.get(10, TimeUnit.SECONDS)).thenThrow(new TimeoutException());

        assertThrows(TimeoutException.class, () -> WebPushNotificationService.awaitResponse(request));

        verify(request).cancel(true);
    }

    private WebPushNotificationService service(String publicKey, String privateKey, String subject) {
        return new WebPushNotificationService(subscriptions, objectMapper, clock, publicKey, privateKey, subject);
    }
}
