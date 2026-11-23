package dev.diyar68.slotify_backend.config;

import dev.diyar68.slotify_backend.entity.*;
import dev.diyar68.slotify_backend.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates only demo-owned data. Never deletes or resets visitor changes. */
@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {

    public static final String EMAIL = "demo@slotify.example";
    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;
    private final AvailabilityRepository availabilities;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String password;

    public DemoDataInitializer(
        ProviderRepository providers,
        ServiceOfferingRepository services,
        AvailabilityRepository availabilities,
        PasswordEncoder encoder,
        Clock clock,
        @Value("${SLOTIFY_DEMO_PASSWORD:}") String password
    ) {
        if (
            password.isBlank() ||
            password.length() < 8 ||
            password.getBytes(StandardCharsets.UTF_8).length > 72
        ) {
            throw new IllegalStateException(
                "SLOTIFY_DEMO_PASSWORD muss gesetzt sein: mindestens 8 Zeichen, maximal 72 UTF-8-Bytes."
            );
        }
        this.providers = providers;
        this.services = services;
        this.availabilities = availabilities;
        this.encoder = encoder;
        this.clock = clock;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Provider provider = providers.findByEmailForUpdate(EMAIL).orElse(null);
        if (provider == null) {
            if (providers.existsByBookingSlug("slotify-demo")) {
                throw new IllegalStateException(
                    "Der Booking-Slug slotify-demo ist bereits vergeben."
                );
            }
            provider = providers.save(
                new Provider("Slotify Demo-Studio", EMAIL, encoder.encode(password), "slotify-demo")
            );
            services.save(new ServiceOffering(provider, "Beratung", 30));
            services.save(new ServiceOffering(provider, "Styling", 45));
        } else if (!encoder.matches(password, provider.getPasswordHash())) {
            // Allows intentional password rotation through hosting configuration.
            provider.setPasswordHash(encoder.encode(password));
            providers.save(provider);
        }
        LocalDate today = LocalDate.now(clock);
        boolean hasFutureWindow = availabilities
            .findAllByProvider(provider)
            .stream()
            .anyMatch(window -> window.getDate().isAfter(today));
        if (!hasFutureWindow) {
            for (int day = 1; day <= 14; day++) {
                availabilities.save(
                    new Availability(
                        provider,
                        today.plusDays(day),
                        LocalTime.of(9, 0),
                        LocalTime.of(17, 0)
                    )
                );
            }
        }
    }
}
