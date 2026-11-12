package org.unipod.unipoderpbackend.controller.notification;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.service.notification.NotificationService;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationApiIntegrationTest {
    private static final String PASSWORD = "notify-password-for-tests";
    private static final String ALICE = "notify-alice@example.test";
    private static final String BOB = "notify-bob@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationService notifications;

    @Test
    void theBellShowsOnlyMyNotificationsAndTracksWhatIRead() throws Exception {
        createUser(ALICE, ApiUserRole.SUBMITTER);
        createUser(BOB, ApiUserRole.REVIEWER);
        UUID briefId = UUID.randomUUID();
        notifications.publish(new NotificationEvent(NotificationType.DOCUMENT_REQUESTED, Set.of(ALICE),
                "Reception needs one more document", "Please upload your recommendation letter.", "submission", briefId));
        notifications.publish(new NotificationEvent(NotificationType.PROJECT_CONFIRMED, Set.of(ALICE),
                "Your project was confirmed", null, "submission", briefId));
        notifications.publish(new NotificationEvent(NotificationType.REVIEWER_ASSIGNED, Set.of(BOB),
                "You have a new brief to review", null, "submission", briefId));
        String alice = login(ALICE);
        String bob = login(BOB);

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].entityId").value(briefId.toString()));
        mockMvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2));

        String firstId = objectMapper.readTree(mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + alice))
                .andReturn().getResponse().getContentAsString()).get("items").get(0).get("id").asText();

        mockMvc.perform(post("/notifications/{id}/read", firstId).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/notifications/{id}/read", firstId).header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(get("/notifications").param("unread", "true").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(1));
        mockMvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.unread").value(0));
        mockMvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void theBellNeedsALogin() throws Exception {
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownNotificationIsNotFound() throws Exception {
        createUser(ALICE, ApiUserRole.SUBMITTER);

        mockMvc.perform(post("/notifications/{id}/read", UUID.randomUUID())
                        .header("Authorization", "Bearer " + login(ALICE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Notification not found"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void publishRefusesToRunOutsideATransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> notifications.publish(new NotificationEvent(
                NotificationType.LOW_STOCK, Set.of(ALICE), "Low stock", null, null, null)));
    }

    private void createUser(String email, ApiUserRole role) {
        users.saveAndFlush(new ApiUser(UUID.randomUUID(), email, passwordEncoder.encode(PASSWORD), role, Instant.now()));
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
