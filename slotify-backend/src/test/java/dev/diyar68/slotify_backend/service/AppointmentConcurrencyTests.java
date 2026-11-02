package dev.diyar68.slotify_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import dev.diyar68.slotify_backend.dto.request.AppointmentRequest;
import dev.diyar68.slotify_backend.entity.*;
import dev.diyar68.slotify_backend.exception.ConflictException;
import dev.diyar68.slotify_backend.repository.*;

// Opt-in only: create-drop must never target the development database.
@EnabledIfEnvironmentVariable(named = "SLOTIFY_TEST_DB_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/slotify_test_[a-z0-9_]+")
@SpringBootTest(properties = {
        "spring.datasource.url=${SLOTIFY_TEST_DB_URL}",
        "spring.datasource.username=${SLOTIFY_TEST_DB_USER:postgres}",
        "spring.datasource.password=${SLOTIFY_TEST_DB_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
class AppointmentConcurrencyTests {
    @Autowired AppointmentService booking;
    @Autowired SlotCalculator slots;
    @Autowired ProviderRepository providers;
    @Autowired ServiceOfferingRepository services;
    @Autowired AvailabilityRepository availabilities;
    @Autowired AppointmentRepository appointments;
    @MockitoBean Clock clock;

    @Test
    void onlyOneConcurrentBookingCommitsAndCancelledBookingsDoNotBlock() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-10T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        LocalDate date = LocalDate.of(2030, 1, 11);
        Provider provider = providers.save(new Provider("Concurrency", "concurrency@example.com", "hash", "concurrency"));
        ServiceOffering service = services.save(new ServiceOffering(provider, "Cut", 30));
        availabilities.save(new Availability(provider, date, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        Appointment cancelled = new Appointment(provider, service, "Cancelled", "cancelled@example.com", "012345",
                date.atTime(9, 30), date.atTime(10, 0));
        cancelled.setStatus(AppointmentStatus.CANCELLED);
        appointments.save(cancelled);
        assertThat(slots.findSlots("concurrency", service.getId(), date)).contains(LocalTime.of(9, 30));
        AppointmentRequest request = new AppointmentRequest(service.getId(), "Max", "max@example.com", "012345", date.atTime(9, 30));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try {
                    booking.book("concurrency", request);
                    return 201;
                } catch (ConflictException ex) {
                    return 409;
                }
            };
            Future<Integer> first = executor.submit(attempt);
            Future<Integer> second = executor.submit(attempt);
            boolean bothReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(bothReady).isTrue();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(appointments.findAllByProviderOrderByStartTimeAscIdAsc(provider).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.BOOKED).count()).isEqualTo(1);
        assertThat(slots.findSlots("concurrency", service.getId(), date)).doesNotContain(LocalTime.of(9, 30));
    }

    @Test
    void cancellationIsPersistedKeepsHistoryAndAllowsRebooking() {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-10T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        LocalDate date = LocalDate.of(2030, 1, 11);
        Provider provider = providers.save(new Provider("Manage", "manage@example.com", "hash", "manage"));
        ServiceOffering service = services.save(new ServiceOffering(provider, "Cut", 30));
        availabilities.save(new Availability(provider, date, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        var later = booking.book("manage", new AppointmentRequest(service.getId(), "Later", "later@example.com",
                "012345", date.atTime(10, 0)));
        var earlier = booking.book("manage", new AppointmentRequest(service.getId(), "Earlier", "earlier@example.com",
                "012345", date.atTime(9, 0)));
        assertThat(booking.findAll("manage@example.com")).extracting(a -> a.id())
                .containsExactly(earlier.id(), later.id());
        assertThat(slots.findSlots("manage", service.getId(), date)).doesNotContain(LocalTime.of(9, 0));

        booking.cancel("manage@example.com", earlier.id());
        booking.cancel("manage@example.com", earlier.id());
        assertThat(appointments.findById(earlier.id()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointments.findAllByProviderOrderByStartTimeAscIdAsc(provider)).hasSize(2);
        assertThat(booking.findOne("manage@example.com", earlier.id()).status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(booking.findAll("manage@example.com")).extracting(a -> a.status())
                .containsExactly(AppointmentStatus.CANCELLED, AppointmentStatus.BOOKED);
        assertThat(slots.findSlots("manage", service.getId(), date)).contains(LocalTime.of(9, 0));
        booking.book("manage", new AppointmentRequest(service.getId(), "New", "new@example.com",
                "012345", date.atTime(9, 0)));
        assertThat(appointments.findAllByProviderOrderByStartTimeAscIdAsc(provider)).hasSize(3);
        assertThat(slots.findSlots("manage", service.getId(), date)).doesNotContain(LocalTime.of(9, 0));
    }
}
