package org.unipod.unipoderpbackend.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupApiIntegrationTest {
    private static final String PASSWORD = "student-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void studentSignsUpAndCanUseTheReturnedToken() throws Exception {
        String response = mockMvc.perform(signup(student("alice@example.test", "2400701234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asString();

        mockMvc.perform(get("/me/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT u.role, p.account_kind, p.student_number, p.university_email, p.rules_version
                FROM api_users u JOIN user_profiles p ON p.user_id = u.id
                WHERE u.email = 'alice@example.test'
                """);
        assertThat(row.get("role")).isEqualTo("SUBMITTER");
        assertThat(row.get("account_kind")).isEqualTo("STUDENT");
        assertThat(row.get("student_number")).isEqualTo("2400701234");
        assertThat(row.get("university_email")).isEqualTo("alice@students.mak.ac.ug");
        assertThat(row.get("rules_version")).isEqualTo("2026-1");

        entityManager.flush();
        Integer audited = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'user.signed_up' AND actor = 'alice@example.test'",
                Integer.class
        );
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void publicSignUpDoesNotNeedStudentDetails() throws Exception {
        mockMvc.perform(signup(Map.of(
                        "accountKind", "PUBLIC",
                        "fullName", "Peter Okello",
                        "phone", "+256 700 000 001",
                        "email", "peter@example.test",
                        "password", PASSWORD,
                        "rulesAccepted", true,
                        "rulesVersion", "2026-1"
                )))
                .andExpect(status().isCreated());
    }

    @Test
    void studentWithoutStudentDetailsGetsFieldErrors() throws Exception {
        mockMvc.perform(signup(Map.of(
                        "accountKind", "STUDENT",
                        "fullName", "Sarah Kintu",
                        "phone", "0700000002",
                        "email", "sarah@example.test",
                        "password", PASSWORD,
                        "rulesAccepted", true,
                        "rulesVersion", "2026-1"
                )))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("studentNumber"));
    }

    @Test
    void organisationNeedsOrganisationName() throws Exception {
        mockMvc.perform(signup(Map.of(
                        "accountKind", "ORGANISATION",
                        "fullName", "Grace Namuli",
                        "phone", "0700000003",
                        "email", "grace@example.test",
                        "password", PASSWORD,
                        "rulesAccepted", true,
                        "rulesVersion", "2026-1"
                )))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("organisationName"));
    }

    @Test
    void rulesMustBeAccepted() throws Exception {
        Map<String, Object> body = new HashMap<>(student("martha@example.test", "2400700001"));
        body.put("rulesAccepted", false);
        mockMvc.perform(signup(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("rulesAccepted"));
    }

    @Test
    void missingRulesAcceptedIsAFieldErrorNotAMalformedBody() throws Exception {
        Map<String, Object> body = new HashMap<>(student("lydia@example.test", "2400700002"));
        body.remove("rulesAccepted");
        mockMvc.perform(signup(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("rulesAccepted"));
    }

    @Test
    void duplicateEmailOrStudentNumberIsAConflict() throws Exception {
        mockMvc.perform(signup(student("joseph@example.test", "2400709999")))
                .andExpect(status().isCreated());

        mockMvc.perform(signup(student("JOSEPH@example.test", "2400708888")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(signup(student("other@example.test", "2400709999")))
                .andExpect(status().isConflict());
    }

    @Test
    void forgotPasswordSendsAOneTimeLinkThatSetsANewPassword() throws Exception {
        mockMvc.perform(signup(student("daniel@example.test", "2400705555")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"Daniel@example.test\"}"))
                .andExpect(status().isAccepted());

        String url = jdbc.queryForObject("""
                SELECT payload ->> 'setPasswordUrl' FROM notification_outbox
                WHERE event_type = 'auth.password_reset' AND payload ->> 'notificationEmail' = 'daniel@example.test'
                """, String.class);
        String token = url.substring(url.indexOf("token=") + "token=".length());

        mockMvc.perform(post("/auth/set-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token,
                                "newPassword", "a-brand-new-password"
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "daniel@example.test",
                                "password", "a-brand-new-password"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordForAnUnknownEmailLooksTheSame() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.test\"}"))
                .andExpect(status().isAccepted());

        Integer queued = jdbc.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE payload ->> 'notificationEmail' = 'nobody@example.test'",
                Integer.class
        );
        assertThat(queued).isZero();
    }

    private RequestBuilder signup(Map<String, Object> body) throws Exception {
        return post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> student(String email, String studentNumber) {
        return Map.ofEntries(
                Map.entry("accountKind", "STUDENT"),
                Map.entry("fullName", "Alice Nabirye"),
                Map.entry("displayFirstName", "Alice"),
                Map.entry("phone", "+256 700 000 000"),
                Map.entry("email", email),
                Map.entry("password", PASSWORD),
                Map.entry("studentNumber", studentNumber),
                Map.entry("course", "BSc Computer Science"),
                Map.entry("universityEmail", "Alice@students.mak.ac.ug"),
                Map.entry("rulesAccepted", true),
                Map.entry("rulesVersion", "2026-1")
        );
    }
}
