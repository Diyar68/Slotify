package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.*;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.mapper.ProviderMapper;
import dev.diyar68.slotify_backend.repository.*;
import dev.diyar68.slotify_backend.service.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    AuthService.class,
    RegistrationService.class,
    ProviderMapper.class,
})
class RegistrationLinkTests {

    private static final String RAW = "a".repeat(43);
    private static final Instant NOW = Instant.parse("2030-01-10T08:00:00Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    ProviderRepository providers;

    @MockitoBean
    RegistrationTokenRepository tokens;

    @MockitoBean
    JavaMailSender mail;

    @MockitoBean
    Clock clock;

    private RegistrationToken token;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        token = new RegistrationToken("verified@example.com", digest(RAW), NOW.plusSeconds(1800));
        when(tokens.findByTokenHash(digest(RAW))).thenReturn(Optional.of(token));
        when(tokens.findByTokenHashForUpdate(digest(RAW))).thenReturn(Optional.of(token));
    }

    @Test
    void sendsSecureLinkStoresOnlyHashAndThrottlesNormalizedEmail() throws Exception {
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content("{\"email\":\"  Link@EXAMPLE.COM  \"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("message").value(RegistrationService.LINK_MESSAGE));
        var saved = org.mockito.ArgumentCaptor.forClass(RegistrationToken.class);
        var sent = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(tokens).save(saved.capture());
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("link@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Slotify – Registrierung abschließen");
        var matcher = Pattern.compile("/register/complete\\?token=([A-Za-z0-9_-]{43})").matcher(
            sent.getValue().getText()
        );
        assertThat(matcher.find()).isTrue();
        String raw = matcher.group(1);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(digest(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getEmail()).isEqualTo("link@example.com");
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content("{\"email\":\"link@example.com\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("message").value(RegistrationService.LINK_MESSAGE));
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content("{\"email\":\"link@example.com\"}")
        ).andExpect(status().isOk());
        verify(mail, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void existingAddressGetsSameNeutralResponseWithoutEmail() throws Exception {
        when(providers.existsByEmail("existing@example.com")).thenReturn(true);
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content("{\"email\":\"existing@example.com\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("message").value(RegistrationService.LINK_MESSAGE));
        verifyNoInteractions(mail);
        verify(tokens, never()).save(any());
    }

    @Test
    void smtpFailureDoesNotRevealWhetherAnAccountExists() throws Exception {
        doThrow(new MailSendException("SMTP failure"))
            .when(mail)
            .send(any(SimpleMailMessage.class));
        mvc.perform(
            post("/api/auth/registration-link")
                .contentType("application/json")
                .content("{\"email\":\"failure@example.com\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("message").value(RegistrationService.LINK_MESSAGE));
    }

    @Test
    void validatesTokenButDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/api/auth/registration-token/validate").param("token", RAW))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"email\":\"verified@example.com\"}"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAndUsedTokensAreRejectedForValidationAndRegistration() throws Exception {
        RegistrationToken expired = new RegistrationToken("verified@example.com", digest(RAW), NOW);
        when(tokens.findByTokenHash(digest(RAW))).thenReturn(Optional.of(expired));
        when(tokens.findByTokenHashForUpdate(digest(RAW))).thenReturn(Optional.of(expired));
        mvc.perform(get("/api/auth/registration-token/validate").param("token", RAW)).andExpect(
            status().isBadRequest()
        );
        mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody())
        ).andExpect(status().isBadRequest());
        token.markUsed(NOW);
        when(tokens.findByTokenHash(digest(RAW))).thenReturn(Optional.of(token));
        when(tokens.findByTokenHashForUpdate(digest(RAW))).thenReturn(Optional.of(token));
        mvc.perform(get("/api/auth/registration-token/validate").param("token", RAW)).andExpect(
            status().isBadRequest()
        );
        mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody())
        ).andExpect(status().isBadRequest());
        verify(providers, never()).save(any());
    }

    @Test
    void oldRegistrationWithoutTokenAndManipulatedTokenAreRejected() throws Exception {
        mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"email\":\"attacker@example.com\",\"name\":\"Test\",\"password\":\"Password123!\"}"
                )
        ).andExpect(status().isBadRequest());
        mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(registerBody().replace(RAW, "b".repeat(43)))
        ).andExpect(status().isBadRequest());
        verify(providers, never()).save(any());
    }

    @Test
    void createsAccountUsingTokenEmailOnlyAndConsumesIt() throws Exception {
        when(providers.save(any(Provider.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );
        mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody())
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("email").value("verified@example.com"))
            .andExpect(jsonPath("password").doesNotExist());
        var saved = org.mockito.ArgumentCaptor.forClass(Provider.class);
        verify(providers).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("verified@example.com");
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody())
        ).andExpect(status().isBadRequest());
        verify(providers, times(1)).save(any());
    }

    @Test
    void failedRegistrationDoesNotConsumeToken() throws Exception {
        when(providers.existsByBookingSlug("test-provider")).thenReturn(true);
        mvc.perform(
            post("/api/auth/register").contentType("application/json").content(registerBody())
        ).andExpect(status().isConflict());
        assertThat(token.getUsedAt()).isNull();
        verify(providers, never()).save(any());
    }

    private String registerBody() {
        return json.writeValueAsString(
            Map.of(
                "token",
                RAW,
                "name",
                "Test",
                "password",
                "Password123!",
                "bookingSlug",
                "test-provider",
                "email",
                "attacker@example.com"
            )
        );
    }

    private String digest(String value) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
