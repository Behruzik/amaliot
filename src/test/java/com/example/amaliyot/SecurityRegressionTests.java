package com.example.amaliyot;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.repository.UserAccountRepository;
import com.example.amaliyot.security.JwtService;
import com.example.amaliyot.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class SecurityRegressionTests {
    @Autowired WebApplicationContext context;
    @Autowired UserAccountRepository users;
    @Autowired JwtService jwt;
    @Autowired AuthService auth;
    private MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void anonymousAndInvalidTokenResponsesAreReadableJsonEvenForPdfRequests() throws Exception {
        mvc.perform(get("/api/profile").accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").isString());
        mvc.perform(get("/api/profile").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void methodSecurityReturns403InsteadOfGeneric500() throws Exception {
        UserAccount user = newUser();
        mvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void disabledUsersAndChangedTokenVersionsCannotReuseAnIssuedToken() throws Exception {
        UserAccount user = newUser();
        String original = bearer(user);
        mvc.perform(get("/api/profile").header("Authorization", original)).andExpect(status().isOk());
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.saveAndFlush(user);
        mvc.perform(get("/api/profile").header("Authorization", original)).andExpect(status().isUnauthorized());
        String newer = bearer(user);
        mvc.perform(get("/api/profile").header("Authorization", newer)).andExpect(status().isOk());
        user.setActive(false);
        users.saveAndFlush(user);
        mvc.perform(get("/api/profile").header("Authorization", newer)).andExpect(status().isUnauthorized());
    }

    @Test
    void loggedOutTokenIsRejectedByTheHttpSecurityChain() throws Exception {
        String authorization = bearer(newUser());
        mvc.perform(get("/api/profile").header("Authorization", authorization)).andExpect(status().isOk());
        auth.logout(authorization);
        mvc.perform(get("/api/profile").header("Authorization", authorization))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private String bearer(UserAccount user) { return "Bearer " + jwt.issue(user).value(); }

    private UserAccount newUser() {
        String suffix = UUID.randomUUID().toString();
        UserAccount user = new UserAccount(null, "test-only-unused-password-" + suffix, "Security", "Test", true);
        user.setEmail("security-" + suffix + "@gmail.com");
        return users.saveAndFlush(user);
    }
}
