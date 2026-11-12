package org.unipod.unipoderpbackend.controller.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.notification.NotificationResponse;
import org.unipod.unipoderpbackend.dto.notification.ReadAllResponse;
import org.unipod.unipoderpbackend.dto.notification.UnreadCountResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

/**
 * The bell (NT1). Every signed-in role has a bell, so there is no role check:
 * the recipient is always the signed-in person, taken from the token, never from the request.
 */
@RestController
@Validated
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
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

    private static String me(Authentication authentication) {
        return CurrentActor.from(authentication).email();
    }
}
