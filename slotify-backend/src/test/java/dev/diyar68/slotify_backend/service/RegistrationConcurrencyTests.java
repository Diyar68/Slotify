package dev.diyar68.slotify_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.diyar68.slotify_backend.dto.request.RegisterRequest;
import dev.diyar68.slotify_backend.entity.RegistrationToken;
import dev.diyar68.slotify_backend.exception.BadRequestException;
import dev.diyar68.slotify_backend.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@EnabledIfEnvironmentVariable(
    named = "SLOTIFY_TEST_DB_URL",
    matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/slotify_test_[a-z0-9_]+"
)
@SpringBootTest(
    properties = {
        "spring.datasource.url=${SLOTIFY_TEST_DB_URL}",
        "spring.datasource.username=${SLOTIFY_TEST_DB_USER:postgres}",
        "spring.datasource.password=${SLOTIFY_TEST_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
    }
)
class RegistrationConcurrencyTests {

    @Autowired
    AuthService auth;

    @Autowired
    ProviderRepository providers;

    @Autowired
    RegistrationTokenRepository tokens;

    @MockitoBean
    JavaMailSender mail;

    @MockitoBean
    Clock clock;

    @Test
    void sameTokenCreatesOnlyOneAccountAndConsumptionIsCommitted() throws Exception {
        Instant now = Instant.parse("2030-01-10T08:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        String raw = "r".repeat(43);
        String hash = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))
        );
        RegistrationToken token = tokens.save(
            new RegistrationToken("parallel-registration@example.com", hash, now.plusSeconds(1800))
        );
        RegisterRequest request = new RegisterRequest(
            "Parallel",
            raw,
            "Password123!",
            "parallel-registration"
        );
        CountDownLatch ready = new CountDownLatch(2),
            start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException(
                    "Start timeout"
                );
                try {
                    auth.register(request);
                    return 201;
                } catch (BadRequestException ex) {
                    return 400;
                }
            };
            Future<Integer> first = executor.submit(attempt),
                second = executor.submit(attempt);
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(bothReady).isTrue();
            assertThat(
                List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(201, 400);
        }
        assertThat(
            providers
                .findAll()
                .stream()
                .filter(p -> p.getEmail().equals("parallel-registration@example.com"))
                .count()
        ).isEqualTo(1);
        assertThat(tokens.findById(token.getId()).orElseThrow().getUsedAt()).isEqualTo(now);
    }
}
