package org.unipod.unipoderpbackend.service.notification;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.notification.PushConfigurationResponse;
import org.unipod.unipoderpbackend.dto.notification.PushSubscriptionRequest;
import org.unipod.unipoderpbackend.model.notification.NotificationCreatedEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationEntityTypes;
import org.unipod.unipoderpbackend.model.notification.WebPushSubscription;
import org.unipod.unipoderpbackend.repository.notification.WebPushSubscriptionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Service
public class WebPushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(WebPushNotificationService.class);
    private static final int TIME_TO_LIVE_SECONDS = 86_400;
    private static final int MAX_ENCRYPTED_PAYLOAD_BYTES = 4096;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final Set<String> PUSH_HOSTS = Set.of(
            "fcm.googleapis.com",
            "push.services.mozilla.com",
            "notify.windows.com",
            "push.apple.com"
    );

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final WebPushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String publicKey;
    private final PushService pushService;

    public WebPushNotificationService(
            WebPushSubscriptionRepository subscriptions,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${notifications.push.public-key:}") String publicKey,
            @Value("${notifications.push.private-key:}") String privateKey,
            @Value("${notifications.push.subject:}") String subject
    ) {
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.publicKey = publicKey.trim();

        boolean noKeys = this.publicKey.isEmpty() && privateKey.isBlank() && subject.isBlank();
        boolean allKeys = !this.publicKey.isEmpty() && !privateKey.isBlank() && !subject.isBlank();
        if (noKeys) {
            this.pushService = null;
        } else if (!allKeys) {
            throw new IllegalStateException(
                    "VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, and VAPID_SUBJECT must be configured together.");
        } else {
            try {
                this.pushService = new PushService(this.publicKey, privateKey.trim(), subject.trim());
                if (!nl.martijndwars.webpush.Utils.verifyKeyPair(
                        this.pushService.getPrivateKey(),
                        this.pushService.getPublicKey())) {
                    throw new IllegalStateException("The configured VAPID key pair does not match.");
                }
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("The configured VAPID keys are invalid.", exception);
            }
        }
    }

    public PushConfigurationResponse configuration() {
        return new PushConfigurationResponse(pushService != null, pushService == null ? null : publicKey);
    }

    @Transactional
    public void subscribe(String recipient, PushSubscriptionRequest request) {
        requireEnabled();
        validateEndpoint(request.endpoint());
        validateSubscriptionKeys(request.p256dh(), request.auth());
        String normalizedRecipient = recipient.trim().toLowerCase(Locale.ROOT);
        WebPushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
                .orElse(null);
        if (subscription != null && !normalizedRecipient.equals(subscription.getRecipient())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This browser subscription is registered to another account.");
        }
        if (subscription == null) {
            subscription = new WebPushSubscription(
                    UUID.randomUUID(),
                    normalizedRecipient,
                    request.endpoint(),
                    request.p256dh(),
                    request.auth(),
                    clock.instant()
            );
        }
        subscription.update(normalizedRecipient, request.endpoint(), request.p256dh(), request.auth());
        subscriptions.save(subscription);
    }

    @Transactional
    public void unsubscribe(String recipient, String endpoint) {
        subscriptions.findByRecipientAndEndpoint(
                recipient.trim().toLowerCase(Locale.ROOT),
                endpoint
        ).ifPresent(subscriptions::delete);
    }

    @Async("webPushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(NotificationCreatedEvent event) {
        if (pushService == null) return;

        for (WebPushSubscription subscription : subscriptions.findByRecipient(event.recipient())) {
            if (!isAllowedEndpoint(subscription.getEndpoint())) {
                log.warn("Skipping notification {} because a stored push endpoint is not allowed.", event.id());
                continue;
            }

            try {
                String payload = payloadFor(event, subscription);
                Notification notification = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dh(),
                        subscription.getAuth(),
                        payload.getBytes(StandardCharsets.UTF_8),
                        TIME_TO_LIVE_SECONDS
                );
                HttpResponse response = awaitResponse(pushService.sendAsync(notification, Encoding.AES128GCM));
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    subscriptions.deleteById(subscription.getId());
                } else if (status < 200 || status >= 300) {
                    log.warn("Push provider rejected notification {} with HTTP {}.", event.id(), status);
                }
            } catch (TimeoutException exception) {
                log.warn("Push delivery timed out for notification {} after {} seconds.",
                        event.id(), SEND_TIMEOUT_SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Push delivery was interrupted for notification {}.", event.id());
                return;
            } catch (PayloadTooLargeException exception) {
                log.warn("Skipping notification {} because its non-body fields exceed the encrypted payload limit.",
                        event.id());
            } catch (JacksonException exception) {
                log.error("Could not encode push notification {}.", event.id(), exception);
            } catch (IllegalArgumentException exception) {
                log.warn("Skipping notification {} because a stored push subscription has invalid encryption keys.",
                        event.id());
            } catch (GeneralSecurityException | IOException | JoseException | ExecutionException exception) {
                log.warn("Push delivery failed for notification {}: {}.",
                        event.id(), exception.getClass().getSimpleName());
            }
        }
    }

    static boolean isAllowedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return PUSH_HOSTS.stream().anyMatch(allowed ->
                    host.equals(allowed) || host.endsWith("." + allowed));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void requireEnabled() {
        if (pushService == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Browser push notifications are not configured.");
        }
    }

    private static void validateEndpoint(String endpoint) {
        if (!isAllowedEndpoint(endpoint)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Push endpoint must use a supported secure browser push service.");
        }
    }

    private static void validateSubscriptionKeys(String p256dh, String auth) {
        try {
            byte[] publicKeyBytes = Base64.getUrlDecoder().decode(p256dh);
            if (publicKeyBytes.length != 65 || publicKeyBytes[0] != 0x04) {
                throw new IllegalArgumentException("Invalid P-256 public key encoding.");
            }
            nl.martijndwars.webpush.Utils.loadPublicKey(publicKeyBytes);

            if (Base64.getUrlDecoder().decode(auth).length != 16) {
                throw new IllegalArgumentException("Invalid authentication secret length.");
            }
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Push subscription encryption keys are invalid.",
                    exception);
        }
    }

    String payloadFor(NotificationCreatedEvent event, WebPushSubscription subscription)
            throws JacksonException, GeneralSecurityException, IOException, JoseException, PayloadTooLargeException {
        String body = event.body() == null ? "" : event.body();
        int bodyCodePoints = body.codePointCount(0, body.length());
        String payload = serializePayload(event, body);
        if (fitsEncryptedPayload(subscription, payload)) {
            return payload;
        }

        int low = 0;
        int high = bodyCodePoints;
        while (low < high) {
            int candidateLength = low + (high - low + 1) / 2;
            String candidateBody = prefixByCodePoints(body, candidateLength);
            String candidatePayload = serializePayload(event, candidateBody);
            if (fitsEncryptedPayload(subscription, candidatePayload)) {
                low = candidateLength;
                payload = candidatePayload;
            } else {
                high = candidateLength - 1;
            }
        }
        if (low == 0) {
            payload = serializePayload(event, "");
            if (!fitsEncryptedPayload(subscription, payload)) {
                throw new PayloadTooLargeException();
            }
        }
        return payload;
    }

    private String serializePayload(NotificationCreatedEvent event, String body) throws JacksonException {
        return objectMapper.writeValueAsString(new PushPayload(
                event.type().name(),
                event.title(),
                body,
                routeFor(event.entityType()),
                "notification-" + event.id()
        ));
    }

    private boolean fitsEncryptedPayload(WebPushSubscription subscription, String payload)
            throws GeneralSecurityException, IOException, JoseException {
        Notification notification = new Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                payload.getBytes(StandardCharsets.UTF_8),
                TIME_TO_LIVE_SECONDS
        );
        long encryptedLength = pushService.preparePost(notification, Encoding.AES128GCM)
                .getEntity()
                .getContentLength();
        return encryptedLength <= MAX_ENCRYPTED_PAYLOAD_BYTES;
    }

    private static String prefixByCodePoints(String value, int codePointCount) {
        return value.substring(0, value.offsetByCodePoints(0, codePointCount));
    }

    static HttpResponse awaitResponse(Future<HttpResponse> request)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return request.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException exception) {
            request.cancel(true);
            throw exception;
        }
    }

    private static String routeFor(String entityType) {
        if (entityType == null) return "/";
        return switch (entityType.toLowerCase(Locale.ROOT)) {
            case NotificationEntityTypes.BOOKING -> "/my-submissions/bookings";
            case "invoice" -> "/my-submissions/invoices";
            case "submission" -> "/my-submissions";
            default -> "/";
        };
    }

    static final class PayloadTooLargeException extends Exception {
    }

    private record PushPayload(String type, String title, String body, String url, String tag) {
    }
}
