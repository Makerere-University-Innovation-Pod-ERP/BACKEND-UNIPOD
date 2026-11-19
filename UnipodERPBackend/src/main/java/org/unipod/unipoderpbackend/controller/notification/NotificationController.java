package org.unipod.unipoderpbackend.controller.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.notification.NotificationResponse;
import org.unipod.unipoderpbackend.dto.notification.PushConfigurationResponse;
import org.unipod.unipoderpbackend.dto.notification.PushSubscriptionRequest;
import org.unipod.unipoderpbackend.dto.notification.ReadAllResponse;
import org.unipod.unipoderpbackend.dto.notification.UnreadCountResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.notification.NotificationService;
import org.unipod.unipoderpbackend.service.notification.WebPushNotificationService;

@RestController
@Validated
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final CurrentActor currentActor;
    private final WebPushNotificationService webPush;

    public NotificationController(
            NotificationService notifications,
            CurrentActor currentActor,
            WebPushNotificationService webPush
    ) {
        this.notifications = notifications;
        this.currentActor = currentActor;
        this.webPush = webPush;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return notifications.list(me(authentication), unread, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication authentication) {
        return new UnreadCountResponse(notifications.unreadCount(me(authentication)));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id, Authentication authentication) {
        return notifications.markRead(me(authentication), id);
    }

    @PostMapping("/read-all")
    public ReadAllResponse markAllRead(Authentication authentication) {
        return new ReadAllResponse(notifications.markAllRead(me(authentication)));
    }

    @GetMapping("/push/config")
    public PushConfigurationResponse pushConfiguration() {
        return webPush.configuration();
    }

    @PutMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(
            @Valid @RequestBody PushSubscriptionRequest request,
            Authentication authentication
    ) {
        webPush.subscribe(me(authentication), request);
    }

    @DeleteMapping("/push/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @RequestParam @NotBlank @Size(max = 2048) String endpoint,
            Authentication authentication
    ) {
        webPush.unsubscribe(me(authentication), endpoint);
    }

    private String me(Authentication authentication) {
        return currentActor.from(authentication).email();
    }
}
