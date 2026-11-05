package org.unipod.unipoderpbackend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorResponseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    @Test
    void directErrorEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
    }

    @Test
    void accessDeniedResponseIncludesBearerChallenge() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        accessDeniedHandler.handle(
                new MockHttpServletRequest("GET", "/api/submissions"),
                response,
                new AccessDeniedException("insufficient role")
        );

        assertEquals(403, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertEquals(
                MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                response.getContentType()
        );
    }
}
