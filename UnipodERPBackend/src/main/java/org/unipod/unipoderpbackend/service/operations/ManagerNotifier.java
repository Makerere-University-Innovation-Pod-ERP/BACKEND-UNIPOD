package org.unipod.unipoderpbackend.service.operations;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.operations.ManagerRecipientQuery;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

/**
 * Tells a department's managers that something needs them: an equipment fault, a loan waiting for approval, stock
 * running low. Call it inside the transaction of the change itself, as {@link NotificationService} requires.
 */
@Service
public class ManagerNotifier {
    private static final int TITLE_MAX = 200;

    private final ManagerRecipientQuery recipients;
    private final NotificationService notifications;

    public ManagerNotifier(ManagerRecipientQuery recipients, NotificationService notifications) {
        this.recipients = recipients;
        this.notifications = notifications;
    }

    /**
     * @return who was told, as role labels (never email addresses); empty when the department has no manager yet
     */
    public List<String> notifyManagers(DepartmentCode department, NotificationType type, String title, String body,
                                       String entityType, UUID entityId) {
        List<String> administrators = recipients.administratorEmails();
        List<String> coordinators = recipients.coordinatorEmails(DepartmentSectorNames.of(department));
        Set<String> everyone = new LinkedHashSet<>(administrators);
        everyone.addAll(coordinators);
        if (everyone.isEmpty()) {
            return List.of();
        }
        notifications.publish(new NotificationEvent(type, everyone, shorten(title), body, entityType, entityId));
        List<String> told = new ArrayList<>();
        if (!administrators.isEmpty()) {
            told.add("Administrators");
        }
        if (!coordinators.isEmpty()) {
            told.add(capitalised(department) + " coordinators");
        }
        return told;
    }

    private static String shorten(String title) {
        if (title.length() <= TITLE_MAX) {
            return title;
        }
        int end = TITLE_MAX;
        if (Character.isHighSurrogate(title.charAt(end - 1))) {
            end--;
        }
        return title.substring(0, end);
    }

    private static String capitalised(DepartmentCode department) {
        String lower = department.name().toLowerCase(Locale.ROOT);
        return department == DepartmentCode.CNC ? "CNC" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
