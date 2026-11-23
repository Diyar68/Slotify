package dev.diyar68.slotify_backend.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.diyar68.slotify_backend.entity.*;
import dev.diyar68.slotify_backend.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoDataInitializerTests {

    @Test
    void seedsOnceUsesHashAndFutureDates() {
        var providers = mock(ProviderRepository.class);
        var services = mock(ServiceOfferingRepository.class);
        var windows = mock(AvailabilityRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var clock = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneId.of("Europe/Berlin"));
        var initializer = new DemoDataInitializer(
            providers,
            services,
            windows,
            encoder,
            clock,
            "DemoTest123!"
        );
        when(providers.findByEmailForUpdate(DemoDataInitializer.EMAIL)).thenReturn(
            Optional.empty()
        );
        when(providers.save(any())).thenAnswer(call -> call.getArgument(0));
        when(windows.findAllByProvider(any())).thenReturn(List.of());
        initializer.run(null);
        var providerCaptor = org.mockito.ArgumentCaptor.forClass(Provider.class);
        verify(providers).save(providerCaptor.capture());
        var provider = providerCaptor.getValue();
        assertThat(encoder.matches("DemoTest123!", provider.getPasswordHash())).isTrue();
        verify(services, times(2)).save(any());
        var windowCaptor = org.mockito.ArgumentCaptor.forClass(Availability.class);
        verify(windows, times(14)).save(windowCaptor.capture());
        assertThat(windowCaptor.getAllValues()).allMatch(window ->
            window.getDate().isAfter(LocalDate.now(clock))
        );
        clearInvocations(providers, services, windows);
        when(providers.findByEmailForUpdate(DemoDataInitializer.EMAIL)).thenReturn(
            Optional.of(provider)
        );
        when(windows.findAllByProvider(provider)).thenReturn(windowCaptor.getAllValues());
        initializer.run(null);
        verify(providers, never()).save(any());
        verify(services, never()).save(any());
        verify(windows, never()).save(any());
    }

    @Test
    void refusesMissingOrBcryptIncompatiblePassword() {
        for (String password : List.of("", "short", "ä".repeat(37))) {
            assertThatThrownBy(() ->
                new DemoDataInitializer(null, null, null, null, null, password)
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SLOTIFY_DEMO_PASSWORD");
        }
    }
}
