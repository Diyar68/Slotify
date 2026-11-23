package dev.diyar68.slotify_backend.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.repository.*;
import dev.diyar68.slotify_backend.service.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.test.context.ActiveProfiles("demo")
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, RegistrationService.class, DemoRegistrationTests.TimeConfig.class })
class DemoRegistrationTests {

    @TestConfiguration
    static class TimeConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService auth;

    @MockitoBean
    ProviderRepository providers;

    @MockitoBean
    RegistrationTokenRepository tokens;

    @MockitoBean
    JavaMailSender mail;

    @Test
    void disablesLinksAndTokenValidationWithoutDatabaseOrMailAccess() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(
            jsonPath("registrationEnabled").value(false)
        );
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content("{\"email\":\"test@example.com\"}")
        ).andExpect(status().isForbidden());
        mvc.perform(
            get("/api/auth/registration-token/validate").param("token", "a".repeat(43))
        ).andExpect(status().isForbidden());
        mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"token\":\"" +
                        "a".repeat(43) +
                        "\",\"name\":\"Demo\",\"password\":\"Password123!\"}"
                )
        ).andExpect(status().isForbidden());
        verifyNoInteractions(auth, tokens, providers, mail);
    }
}
