package dev.diyar68.slotify_backend.service;

import dev.diyar68.slotify_backend.entity.RegistrationToken;
import dev.diyar68.slotify_backend.exception.BadRequestException;
import dev.diyar68.slotify_backend.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    @Value("${slotify.registration-enabled:true}")
    private boolean registrationEnabled = true;

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void requireRegistrationEnabled() {
        if (!registrationEnabled) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "Die Registrierung ist in der Demo deaktiviert. Bitte verwende den Demo-Testzugang."
        );
    }

    public static final String LINK_MESSAGE =
        "Falls die E-Mail-Adresse registriert werden kann, haben wir dir einen Registrierungslink gesendet.";
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private final RegistrationTokenRepository tokens;
    private final ProviderRepository providers;
    private final JavaMailSender mail;
    private final Clock clock;
    private final String frontendUrl;
    private final String sender;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Instant> recentRequests = new ConcurrentHashMap<>();

    public RegistrationService(
        RegistrationTokenRepository tokens,
        ProviderRepository providers,
        JavaMailSender mail,
        Clock clock,
        @Value("${slotify.frontend-url:http://localhost:5173}") String frontendUrl,
        @Value("${slotify.mail-from:no-reply@slotify.local}") String sender
    ) {
        this.tokens = tokens;
        this.providers = providers;
        this.mail = mail;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.sender = sender;
    }

    @Transactional
    public void requestLink(String email) {
        requireRegistrationEnabled();
        Instant now = clock.instant();
        // Atomic per-email cooldown for this single-instance MVP; old entries are removed.
        recentRequests
            .entrySet()
            .removeIf(entry -> !entry.getValue().isAfter(now.minusSeconds(60)));
        if (
            recentRequests.putIfAbsent(email, now) != null || providers.existsByEmail(email)
        ) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RegistrationToken(email, hash(rawToken), now.plus(Duration.ofMinutes(30))));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject("Slotify – Registrierung abschließen");
        message.setText(
            "Willkommen bei Slotify!\n\nKlicke auf den folgenden Link, um deine Registrierung abzuschließen:\n\n" +
                frontendUrl +
                "/register/complete?token=" +
                rawToken +
                "\n\nDer Link ist 30 Minuten gültig.\n\nFalls du diese Registrierung nicht angefordert hast, kannst du diese E-Mail ignorieren."
        );
        try {
            mail.send(message);
        } catch (MailException ex) {
            // Keep the response neutral, including SMTP failures. Never log address, token or message.
            log.warn(
                "Registration email could not be sent. Check SMTP configuration and availability."
            );
        }
    }

    @Transactional(readOnly = true)
    public String validate(String rawToken) {
        requireRegistrationEnabled();
        RegistrationToken token = tokens
            .findByTokenHash(hashChecked(rawToken))
            .orElseThrow(this::invalidLink);
        check(token);
        return token.getEmail();
    }

    // Called within AuthService.register's transaction; the lock lasts until account + consumption commit.
    public RegistrationToken lockValidToken(String rawToken) {
        requireRegistrationEnabled();
        RegistrationToken token = tokens
            .findByTokenHashForUpdate(hashChecked(rawToken))
            .orElseThrow(this::invalidLink);
        check(token);
        return token;
    }

    public void markUsed(RegistrationToken token) {
        token.markUsed(clock.instant());
    }

    private void check(RegistrationToken token) {
        if (
            token.getUsedAt() != null || !token.getExpiresAt().isAfter(clock.instant())
        ) throw invalidLink();
    }

    private String hashChecked(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw invalidLink();
        return hash(rawToken);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    private BadRequestException invalidLink() {
        return new BadRequestException(
            "Der Registrierungslink ist ungültig oder abgelaufen. Bitte fordere einen neuen Link an."
        );
    }
}
