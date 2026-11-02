package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.*;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.repository.*;
import dev.diyar68.slotify_backend.service.*;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest({AppointmentController.class, PublicSlotController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AppointmentService.class, SlotCalculator.class})
class AppointmentTests {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 11);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean ProviderRepository providers;
    @MockitoBean ServiceOfferingRepository services;
    @MockitoBean AvailabilityRepository availabilities;
    @MockitoBean AppointmentRepository appointments;
    @MockitoBean Clock clock;
    private Provider provider;
    private ServiceOffering service;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-10T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        provider = new Provider("Devo", "private@example.com", "private-hash", "barber-devo");
        service = new ServiceOffering(provider, "Haarschnitt", 30);
        ReflectionTestUtils.setField(service, "id", 1L);
        when(providers.findByBookingSlugForUpdate("barber-devo")).thenReturn(Optional.of(provider));
        when(providers.findByBookingSlug("barber-devo")).thenReturn(Optional.of(provider));
        when(services.findByIdAndProvider(1L, provider)).thenReturn(Optional.of(service));
        when(availabilities.findAllByProviderAndDate(provider, DATE)).thenReturn(List.of(
                new Availability(provider, DATE, LocalTime.of(9, 0), LocalTime.of(11, 0))));
    }

    @Test
    void booksAnonymouslyWithoutCsrfAndIgnoresClientControlledFields() throws Exception {
        when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            ReflectionTestUtils.setField(appointment, "id", 10L);
            return appointment;
        });
        Map<String, Object> request = request();
        request.put("providerId", 999);
        request.put("status", "CANCELLED");
        request.put("endTime", "2030-01-11T23:00:00");
        String body = mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("id").value(10))
                .andExpect(jsonPath("status").value("BOOKED"))
                .andExpect(jsonPath("serviceId").value(1))
                .andExpect(jsonPath("endTime").value("2030-01-11T10:00:00"))
                .andExpect(jsonPath("provider").doesNotExist())
                .andExpect(jsonPath("customerEmail").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments).save(saved.capture());
        assertThat(saved.getValue().getProvider()).isSameAs(provider);
        assertThat(saved.getValue().getServiceOffering()).isSameAs(service);
        assertThat(saved.getValue().getCustomerName()).isEqualTo("Max Mustermann");
        assertThat(body).doesNotContain("private@example.com", "private-hash", "max@example.com");
        var order = inOrder(providers, services, availabilities, appointments);
        order.verify(providers).findByBookingSlugForUpdate("barber-devo");
        order.verify(services).findByIdAndProvider(1L, provider);
        order.verify(appointments).findOverlapping(provider, AppointmentStatus.BOOKED,
                DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay());
        order.verify(availabilities).findAllByProviderAndDate(provider, DATE);
        order.verify(appointments).save(any(Appointment.class));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(Arguments.of("customerName", " "), Arguments.of("customerName", "a".repeat(101)),
                Arguments.of("customerEmail", "invalid"), Arguments.of("customerPhone", ""),
                Arguments.of("serviceId", 0), Arguments.of("startTime", "invalid"),
                Arguments.of("startTime", null));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidCustomerData(String field, Object value) throws Exception {
        Map<String, Object> request = request(); request.put(field, value);
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        verifyNoInteractions(providers, services, availabilities, appointments);
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(providers, services, availabilities, appointments);
    }

    @Test
    void rejectsUnknownProvider() throws Exception {
        mvc.perform(post("/api/public/unknown/appointments").contentType("application/json")
                .content(json.writeValueAsString(request()))).andExpect(status().isNotFound());
        verifyNoInteractions(services, availabilities, appointments);
    }

    @Test
    void rejectsMissingOrForeignService() throws Exception {
        Map<String, Object> request = request(); request.put("serviceId", 2L);
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isNotFound());
        verify(services).findByIdAndProvider(2L, provider);
        verifyNoInteractions(availabilities, appointments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-09T09:00:00", "2030-01-10T09:00:00"})
    void rejectsPastOrPresentStart(String start) throws Exception {
        Map<String, Object> request = request(); request.put("startTime", start);
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        verify(appointments, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-11T08:30:00", "2030-01-11T10:45:00", "2030-01-12T09:30:00", "2030-01-11T09:15:00"})
    void rejectsUnavailableOrOffGridStart(String start) throws Exception {
        Map<String, Object> request = request(); request.put("startTime", start);
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isConflict());
        verify(appointments, never()).save(any());
    }

    @Test
    void bookedIntervalsBlockBookingAndDisappearFromSlots() throws Exception {
        when(appointments.findOverlapping(provider, AppointmentStatus.BOOKED, DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(existing(AppointmentStatus.BOOKED)));
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request()))).andExpect(status().isConflict());
        mvc.perform(get("/api/public/barber-devo/slots").param("serviceId", "1").param("date", DATE.toString()))
                .andExpect(status().isOk()).andExpect(content().json("[\"09:00:00\",\"10:00:00\",\"10:30:00\"]"));
        verify(appointments, never()).save(any());
    }

    @Test
    void cancelledIntervalsDoNotBlock() throws Exception {
        when(appointments.findOverlapping(provider, AppointmentStatus.BOOKED, DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(existing(AppointmentStatus.CANCELLED)));
        when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request()))).andExpect(status().isCreated());
    }

    @Test
    void differentServiceStillBlocksAndAdjacentAppointmentIsAllowed() throws Exception {
        ServiceOffering other = new ServiceOffering(provider, "Other", 45);
        Appointment existing = new Appointment(provider, other, "Other customer", "other@example.com", "012345",
                DATE.atTime(9, 0), DATE.atTime(9, 45));
        when(appointments.findOverlapping(provider, AppointmentStatus.BOOKED, DATE.atStartOfDay(), DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(existing));
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request()))).andExpect(status().isConflict());
        // A 45-minute service's own raster contains 09:45, immediately after the existing booking.
        service.setDuration(45);
        when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> request = request(); request.put("startTime", "2030-01-11T09:45:00");
        mvc.perform(post("/api/public/barber-devo/appointments").contentType("application/json")
                .content(json.writeValueAsString(request))).andExpect(status().isCreated());
    }

    private Appointment existing(AppointmentStatus status) {
        Appointment appointment = new Appointment(provider, service, "Other", "other@example.com", "012345",
                DATE.atTime(9, 30), DATE.atTime(10, 0));
        appointment.setStatus(status); return appointment;
    }

    private Map<String, Object> request() {
        return new LinkedHashMap<>(Map.of("serviceId", 1, "customerName", "Max Mustermann",
                "customerEmail", "max@example.com", "customerPhone", "017612345678", "startTime", "2030-01-11T09:30:00"));
    }
}
