package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.mapper.ProviderMapper;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.service.AuthService;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthService.class, ProviderMapper.class})
class RegistrationTests {
    private static final String PASSWORD = "MeinPasswort123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean ProviderRepository repository;

    @Test
    void registersAnonymouslyWithCustomSlugAndHashesPassword() throws Exception {
        when(repository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String body = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("name").value("Barber Devo"))
                .andExpect(jsonPath("email").value("devo@example.com"))
                .andExpect(jsonPath("bookingSlug").value("barber-devo"))
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<Provider> saved = ArgumentCaptor.forClass(Provider.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Barber Devo");
        assertThat(saved.getValue().getEmail()).isEqualTo("devo@example.com");
        assertThat(saved.getValue().getBookingSlug()).isEqualTo("barber-devo");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
        assertThat(body).doesNotContain(PASSWORD, saved.getValue().getPasswordHash());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "empty"})
    void generatesSlugWhenNoCustomSlugIsProvided(String variant) throws Exception {
        Map<String, Object> request = validRequest();
        if (variant.equals("missing")) {
            request.remove("bookingSlug");
        } else {
            request.put("bookingSlug", variant.equals("null") ? null : "");
        }
        when(repository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String body = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<Provider> saved = ArgumentCaptor.forClass(Provider.class);
        verify(repository).save(saved.capture());
        String slug = saved.getValue().getBookingSlug();
        assertThat(slug).matches("barber-devo-[a-z0-9]{6}");
        verify(repository).existsByBookingSlug(slug);
        assertThat(objectMapper.readTree(body).get("bookingSlug").asString()).isEqualTo(slug);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateCustomSlugWithoutSaving() throws Exception {
        when(repository.existsByBookingSlug("barber-devo")).thenReturn(true);

        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("status").value(409))
                .andExpect(jsonPath("message").value("This Slug already exists, take another one!"));

        verify(repository, never()).save(any(Provider.class));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("name", null),
                Arguments.of("name", "   "),
                Arguments.of("name", "a".repeat(101)),
                Arguments.of("email", null),
                Arguments.of("email", "not-an-email"),
                Arguments.of("bookingSlug", "Devran"),
                Arguments.of("bookingSlug", "dev--ran"),
                Arguments.of("bookingSlug", "-devran"),
                Arguments.of("bookingSlug", "devran-"),
                Arguments.of("bookingSlug", "a".repeat(101)));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidFieldsBeforeCallingServiceRepository(String field, String value) throws Exception {
        Map<String, Object> request = validRequest();
        request.put(field, value);

        String body = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("status").value(400))
                .andExpect(jsonPath("message").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD);
        verifyNoInteractions(repository);
    }

    @Test
    void keepsOtherEndpointsProtected() throws Exception {
        mvc.perform(get("/api/providers")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    private Map<String, Object> validRequest() {
        return new LinkedHashMap<>(Map.of("name", "Barber Devo", "email", "devo@example.com",
                "password", PASSWORD, "bookingSlug", "barber-devo"));
    }
}
