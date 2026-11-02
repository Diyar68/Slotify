package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.*;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.repository.*;
import dev.diyar68.slotify_backend.service.*;

@WebMvcTest({AppointmentController.class, PublicSlotController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AppointmentService.class, SlotCalculator.class})
@WithMockUser(username = "a@example.com", roles = "PROVIDER")
class ProviderAppointmentTests {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 11);
    @Autowired MockMvc mvc;
    @MockitoBean ProviderRepository providers;
    @MockitoBean ServiceOfferingRepository services;
    @MockitoBean AvailabilityRepository availabilities;
    @MockitoBean AppointmentRepository appointments;
    @MockitoBean Clock clock;
    private Provider providerA;
    private Provider providerB;
    private ServiceOffering service;
    private Appointment own;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-10T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        providerA = new Provider("A", "a@example.com", "secret-hash", "provider-a");
        providerB = new Provider("B", "b@example.com", "other-hash", "provider-b");
        service = new ServiceOffering(providerA, "Haarschnitt", 30);
        ReflectionTestUtils.setField(service, "id", 1L);
        own = appointment(10L, providerA, 9);
        when(providers.findByEmail("a@example.com")).thenReturn(Optional.of(providerA));
        when(providers.findByEmailForUpdate("a@example.com")).thenReturn(Optional.of(providerA));
        when(appointments.findByIdAndProvider(10L, providerA)).thenReturn(Optional.of(own));
    }

    @Test
    void listsOnlyOwnBookedAndCancelledAppointments() throws Exception {
        Appointment cancelled = appointment(11L, providerA, 10);
        cancelled.setStatus(AppointmentStatus.CANCELLED);
        when(appointments.findAllByProviderOrderByStartTimeAscIdAsc(providerA)).thenReturn(List.of(own, cancelled));
        when(appointments.findAllByProviderOrderByStartTimeAscIdAsc(providerB))
                .thenReturn(List.of(appointment(20L, providerB, 11)));
        String body = mvc.perform(get("/api/appointments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status").value("BOOKED"))
                .andExpect(jsonPath("$[0].serviceOffering.name").value("Haarschnitt"))
                .andExpect(jsonPath("$[0].customerName").value("Max"))
                .andExpect(jsonPath("$[0].customerEmail").value("max@example.com"))
                .andExpect(jsonPath("$[0].customerPhone").value("017612345678"))
                .andExpect(jsonPath("$[0].startTime").value("2030-01-11T09:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("2030-01-11T09:30:00"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].provider").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret-hash", "b@example.com", "other-hash");
        verify(appointments).findAllByProviderOrderByStartTimeAscIdAsc(providerA);
        verify(appointments, never()).findAllByProviderOrderByStartTimeAscIdAsc(providerB);
    }

    @Test
    void returnsEmptyListWithoutAppointments() throws Exception {
        mvc.perform(get("/api/appointments")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void returnsOwnDetails() throws Exception {
        mvc.perform(get("/api/appointments/10")).andExpect(status().isOk())
                .andExpect(jsonPath("id").value(10))
                .andExpect(jsonPath("serviceOffering.id").value(1))
                .andExpect(jsonPath("customerEmail").value("max@example.com"));
        verify(appointments).findByIdAndProvider(10L, providerA);
    }

    @Test
    void cancellationIsIdempotentAndDoesNotDelete() throws Exception {
        mvc.perform(patch("/api/appointments/10/cancel").with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("CANCELLED"));
        mvc.perform(patch("/api/appointments/10/cancel").with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("CANCELLED"));
        assertThat(own.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointments, times(1)).save(own);
        verify(appointments, never()).delete(any(Appointment.class));
        verify(appointments, never()).deleteById(any());
        mvc.perform(get("/api/appointments/10")).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("CANCELLED"));
    }

    @Test
    void cancelledTimeBecomesFreeAndBookableAgain() throws Exception {
        when(providers.findByBookingSlug("provider-a")).thenReturn(Optional.of(providerA));
        when(providers.findByBookingSlugForUpdate("provider-a")).thenReturn(Optional.of(providerA));
        when(services.findByIdAndProvider(1L, providerA)).thenReturn(Optional.of(service));
        when(availabilities.findAllByProviderAndDate(providerA, DATE)).thenReturn(List.of(
                new Availability(providerA, DATE, LocalTime.of(9, 0), LocalTime.of(9, 30))));
        when(appointments.findOverlapping(providerA, AppointmentStatus.BOOKED, DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay()))
                .thenAnswer(invocation -> own.getStatus() == AppointmentStatus.BOOKED ? List.of(own) : List.of());
        mvc.perform(get("/api/public/provider-a/slots").param("serviceId", "1").param("date", DATE.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(patch("/api/appointments/10/cancel").with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/public/provider-a/slots").param("serviceId", "1").param("date", DATE.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[\"09:00:00\"]"));
        when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/api/public/provider-a/appointments").contentType("application/json")
                .content("""
                        {"serviceId":1,"customerName":"New customer","customerEmail":"new@example.com",
                         "customerPhone":"012345678","startTime":"2030-01-11T09:00:00"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("status").value("BOOKED"));
    }

    @ParameterizedTest
    @ValueSource(longs = {20, 999})
    void rejectsForeignAndMissingDetailsAndCancellation(long id) throws Exception {
        Appointment foreign = appointment(20L, providerB, 11);
        when(appointments.findByIdAndProvider(20L, providerB)).thenReturn(Optional.of(foreign));
        mvc.perform(get("/api/appointments/{id}", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("status").value(404));
        mvc.perform(patch("/api/appointments/{id}/cancel", id).with(csrf())).andExpect(status().isNotFound())
                .andExpect(jsonPath("status").value(404));
        verify(appointments, times(2)).findByIdAndProvider(id, providerA);
        verify(appointments, never()).save(any());
        assertThat(foreign.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void requiresCsrfForCancellation() throws Exception {
        mvc.perform(patch("/api/appointments/10/cancel")).andExpect(status().isForbidden());
        verifyNoInteractions(providers, appointments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/appointments", "/api/appointments/10", "/api/appointments/10/cancel"})
    @WithAnonymousUser
    void rejectsAnonymousRequests(String path) throws Exception {
        var request = path.endsWith("cancel") ? patch(path) : get(path);
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
        verifyNoInteractions(providers, appointments);
    }

    @Test
    void allowsReactCancellationPreflight() throws Exception {
        mvc.perform(options("/api/appointments/10/cancel").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private Appointment appointment(Long id, Provider provider, int hour) {
        Appointment appointment = new Appointment(provider, service, "Max", "max@example.com", "017612345678",
                DATE.atTime(hour, 0), DATE.atTime(hour, 30));
        ReflectionTestUtils.setField(appointment, "id", id);
        return appointment;
    }
}
