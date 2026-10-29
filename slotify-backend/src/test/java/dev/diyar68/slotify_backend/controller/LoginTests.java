package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.mapper.ProviderMapper;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.service.AuthService;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthService.class, ProviderMapper.class})
class LoginTests {
    private static final String PASSWORD = "Password123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean ProviderRepository repository;

    @Test
    void logsInNormalizesEmailRotatesSessionAndKeepsAuthentication() throws Exception {
        existingProvider();
        MvcResult csrf = csrf(null);
        MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession();
        String oldId = session.getId();
        String body = mvc.perform(post("/api/auth/login").session(session)
                .header("X-CSRF-TOKEN", token(csrf)).contentType("application/json")
                .content(credentials("  DEVO@EXAMPLE.COM  ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("email").value("devo@example.com"))
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD);
        assertThat(session.getId()).isNotEqualTo(oldId);
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getName()).isEqualTo("devo@example.com");
        assertThat(context.getAuthentication().getCredentials()).isNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("bookingSlug").value("barber-devo"))
                .andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"devo@example.com, wrong-password", "unknown@example.com, Password123!"})
    void rejectsWrongCredentialsWithSameGenericMessage(String email, String password) throws Exception {
        existingProvider();
        MvcResult csrf = csrf(null);
        MockHttpSession session = (MockHttpSession) csrf.getRequest().getSession();
        mvc.perform(post("/api/auth/login").session(session)
                .header("X-CSRF-TOKEN", token(csrf)).contentType("application/json")
                .content(credentials(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("status").value(401))
                .andExpect(jsonPath("message").value("Invalid email or password"));
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test
    void rejectsOverlongPasswordEvenIfBcryptPrefixMatches() throws Exception {
        when(repository.findByEmail("devo@example.com")).thenReturn(Optional.of(new Provider(
                "Barber Devo", "devo@example.com", passwordEncoder.encode("a".repeat(72)), "barber-devo")));
        MvcResult csrf = csrf(null);
        mvc.perform(post("/api/auth/login").session((MockHttpSession) csrf.getRequest().getSession())
                .header("X-CSRF-TOKEN", token(csrf)).contentType("application/json")
                .content(credentials("devo@example.com", "a".repeat(73))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnonymousMe() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("status").value(401));
        verifyNoInteractions(repository);
    }

    @Test
    void requiresCsrfForLogin() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(credentials("devo@example.com", PASSWORD))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void logoutRequiresFreshCsrfAndInvalidatesSession() throws Exception {
        existingProvider();
        MvcResult initial = csrf(null);
        MockHttpSession session = (MockHttpSession) initial.getRequest().getSession();
        mvc.perform(post("/api/auth/login").session(session)
                .header("X-CSRF-TOKEN", token(initial)).contentType("application/json")
                .content(credentials("devo@example.com", PASSWORD))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session)
                .header("X-CSRF-TOKEN", token(initial))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        MvcResult refreshed = csrf(session);
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", token(refreshed)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getCookie("JSESSIONID").getMaxAge()).isZero());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void allowsReactCorsPreflightWithCredentials() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private void existingProvider() {
        when(repository.findByEmail("devo@example.com")).thenReturn(Optional.of(new Provider(
                "Barber Devo", "devo@example.com", passwordEncoder.encode(PASSWORD), "barber-devo")));
    }

    private MvcResult csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) request.session(session);
        return mvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private String token(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private String credentials(String email, String password) {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }
}
