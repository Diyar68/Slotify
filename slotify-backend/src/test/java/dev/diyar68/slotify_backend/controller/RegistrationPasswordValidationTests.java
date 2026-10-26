package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.dto.request.RegisterRequest;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.mapper.ProviderMapper;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.service.AuthService;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthService.class, ProviderMapper.class})
class RegistrationPasswordValidationTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean ProviderRepository repository;

    static Stream<String> oversizedPasswords() {
        return Stream.of("a".repeat(73), "a".repeat(151), "ä".repeat(37), "€".repeat(25), "😀".repeat(19));
    }

    @ParameterizedTest
    @MethodSource("oversizedPasswords")
    void rejectsPasswordsBeyondBcryptByteLimit(String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                .contentType("application/json").content(requestJson(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("status").value(400))
                .andExpect(jsonPath("message").value("password: Password must not exceed 72 UTF-8 bytes"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(password);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"       ", "short12"})
    void rejectsMissingBlankAndShortPasswords(String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                .contentType("application/json").content(requestJson(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("status").value(400))
                .andExpect(jsonPath("message").isString())
                .andReturn().getResponse().getContentAsString();

        if (password != null && !password.isEmpty()) {
            assertThat(body).doesNotContain(password);
        }
        verifyNoInteractions(repository);
    }

    static Stream<String> acceptedPasswords() {
        return Stream.of("a".repeat(8), "a".repeat(72), "ä".repeat(36), "€".repeat(24), "😀".repeat(18));
    }

    @ParameterizedTest
    @MethodSource("acceptedPasswords")
    void registersAndHashesAcceptedPasswords(String password) throws Exception {
        when(repository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider provider = invocation.getArgument(0);
            assertThat(provider.getPasswordHash()).isNotEqualTo(password);
            assertThat(passwordEncoder.matches(password, provider.getPasswordHash())).isTrue();
            return provider;
        });

        mvc.perform(post("/api/auth/register")
                .contentType("application/json").content(requestJson(password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("bookingSlug").value("barber-devo"))
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist());
    }

    private String requestJson(String password) {
        return objectMapper.writeValueAsString(new RegisterRequest(
                "Barber Devo", "devo@example.com", password, "barber-devo"));
    }
}
