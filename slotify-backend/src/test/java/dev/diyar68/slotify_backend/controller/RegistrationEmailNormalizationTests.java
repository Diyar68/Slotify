package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.entity.RegistrationToken;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.mapper.ProviderMapper;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.service.AuthService;
import dev.diyar68.slotify_backend.service.RegistrationService;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    AuthService.class,
    ProviderMapper.class,
})
class RegistrationEmailNormalizationTests {

    @MockitoBean
    RegistrationService registration;

    private static final String TOKEN = "a".repeat(43);

    @BeforeEach
    void registrationToken() {
        when(registration.lockValidToken(TOKEN)).thenReturn(
            new RegistrationToken("test@example.com", "hash", Instant.now().plusSeconds(1800))
        );
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ProviderRepository repository;

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Test@example.com",
            "test@example.com",
            "  TEST@EXAMPLE.COM  ",
            "\tTest@example.com\n",
        }
    )
    void normalizesEmailBeforeRequestingLink(String email) throws Exception {
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("email", email)))
        ).andExpect(status().isOk());
        verify(registration).requestLink("test@example.com");
        verifyNoInteractions(repository);
    }

    @Test
    void returnsConflictForExistingNormalizedEmail() throws Exception {
        when(repository.existsByEmail("test@example.com")).thenReturn(true);
        mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(requestJson("  Test@EXAMPLE.COM  "))
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("status").value(409));
        verify(repository).existsByEmail("test@example.com");
        verify(repository, never()).save(any(Provider.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "   ", " test @example.com " })
    void rejectsBlankOrInternallySpacedEmail(String email) throws Exception {
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("email", email)))
        ).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void returnsConflictForDatabaseUniqueViolation() throws Exception {
        when(repository.save(any(Provider.class))).thenThrow(
            new DataIntegrityViolationException(
                "internal database details",
                new SQLException("internal SQL", "23505")
            )
        );
        mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(requestJson("Test@example.com"))
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("status").value(409))
            .andExpect(jsonPath("message").value("Email or booking slug already exists"));
    }

    @Test
    void doesNotMisreportOtherIntegrityFailuresAsDuplicates() throws Exception {
        when(repository.save(any(Provider.class))).thenThrow(
            new DataIntegrityViolationException(
                "internal database details",
                new SQLException("internal SQL", "23502")
            )
        );
        mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(requestJson("Test@example.com"))
        )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("message").value("Registration could not be saved"));
    }

    @Test
    void normalizesEmailOnEntityCreationAndUpdate() {
        Provider provider = new Provider("Test", "  TEST@EXAMPLE.COM  ", "hash", "test");
        assertThat(provider.getEmail()).isEqualTo("test@example.com");
        provider.setEmail("  OTHER@EXAMPLE.COM ");
        assertThat(provider.getEmail()).isEqualTo("other@example.com");
        assertThat(Provider.normalizeEmail(null)).isNull();
    }

    private String requestJson(String email) {
        // A client-supplied email cannot override the email bound to the token.
        return objectMapper.writeValueAsString(
            Map.of(
                "name",
                "Test Provider",
                "token",
                TOKEN,
                "email",
                email,
                "password",
                "Password123!",
                "bookingSlug",
                "test-provider"
            )
        );
    }
}
