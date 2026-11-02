package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.Availability;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.entity.ServiceOffering;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.repository.AvailabilityRepository;
import dev.diyar68.slotify_backend.repository.AppointmentRepository;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.repository.ServiceOfferingRepository;
import dev.diyar68.slotify_backend.service.SlotCalculator;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PublicSlotController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SlotCalculator.class})
class PublicSlotTests {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 11);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ProviderRepository providers;
    @MockitoBean ServiceOfferingRepository services;
    @MockitoBean AvailabilityRepository availabilities;
    @MockitoBean Clock clock;
    @MockitoBean AppointmentRepository appointments;
    private Provider provider;
    private ServiceOffering service;

    @BeforeEach
    void setup() {
        // 09:10 in the application's Berlin timezone, not 08:10 UTC.
        when(clock.instant()).thenReturn(Instant.parse("2030-01-10T08:10:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        provider = new Provider("Barber Devo", "devo@example.com", "private-hash", "barber-devo");
        service = new ServiceOffering(provider, "Haarschnitt", 30);
        when(providers.findByBookingSlug("barber-devo")).thenReturn(Optional.of(provider));
        when(services.findByIdAndProvider(1L, provider)).thenReturn(Optional.of(service));
    }

    @Test
    void anonymouslyReturnsThirtyMinuteSlots() throws Exception {
        when(availabilities.findAllByProviderAndDate(provider, DATE))
                .thenReturn(List.of(window(DATE, "09:00", "12:00")));
        assertSlots(DATE, "09:00", "09:30", "10:00", "10:30", "11:00", "11:30");
    }

    @Test
    void returnsFortyFiveMinuteSlots() throws Exception {
        service.setDuration(45);
        when(availabilities.findAllByProviderAndDate(provider, DATE))
                .thenReturn(List.of(window(DATE, "09:00", "12:00")));
        assertSlots(DATE, "09:00", "09:45", "10:30", "11:15");
    }

    @Test
    void sortsAndDeduplicatesMultipleWindows() throws Exception {
        when(availabilities.findAllByProviderAndDate(provider, DATE)).thenReturn(List.of(
                window(DATE, "12:00", "13:00"), window(DATE, "09:00", "10:00"),
                window(DATE, "09:00", "10:00")));
        assertSlots(DATE, "09:00", "09:30", "12:00", "12:30");
    }

    @Test
    void doesNotCombineAdjacentWindowsThatAreIndividuallyTooShort() throws Exception {
        service.setDuration(45);
        when(availabilities.findAllByProviderAndDate(provider, DATE)).thenReturn(List.of(
                window(DATE, "09:00", "09:30"), window(DATE, "09:30", "10:00")));
        assertSlots(DATE);
    }

    @Test
    void neverWrapsPastMidnight() throws Exception {
        when(availabilities.findAllByProviderAndDate(provider, DATE))
                .thenReturn(List.of(window(DATE, "23:00", "23:59")));
        assertSlots(DATE, "23:00");
    }

    @Test
    void handlesVeryLongServiceWithoutOverflowOrLooping() throws Exception {
        service.setDuration(Integer.MAX_VALUE);
        when(availabilities.findAllByProviderAndDate(provider, DATE))
                .thenReturn(List.of(window(DATE, "09:00", "12:00")));
        assertSlots(DATE);
    }

    @Test
    void removesPastStartsWithoutShiftingTheGrid() throws Exception {
        LocalDate today = LocalDate.of(2030, 1, 10);
        when(availabilities.findAllByProviderAndDate(provider, today))
                .thenReturn(List.of(window(today, "09:00", "11:00")));
        assertSlots(today, "09:30", "10:00", "10:30");
    }

    @Test
    void excludesStartExactlyNow() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2030-01-10T08:00:00Z"));
        LocalDate today = LocalDate.of(2030, 1, 10);
        when(availabilities.findAllByProviderAndDate(provider, today))
                .thenReturn(List.of(window(today, "09:00", "09:30")));
        assertSlots(today);
    }

    @Test
    void returnsEmptyListForPastDate() throws Exception {
        assertSlots(LocalDate.of(2030, 1, 9));
        verifyNoInteractions(availabilities);
    }

    @Test
    void returnsEmptyListWithoutAvailability() throws Exception {
        assertSlots(DATE);
    }

    @Test
    void returnsNotFoundForUnknownSlug() throws Exception {
        mvc.perform(get("/api/public/unknown/slots").param("serviceId", "1").param("date", DATE.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("status").value(404));
        verifyNoInteractions(services, availabilities);
    }

    @ParameterizedTest
    @ValueSource(longs = {2, 999})
    void returnsNotFoundForForeignOrMissingService(long id) throws Exception {
        Provider other = new Provider("Other", "other@example.com", "hash", "other");
        when(services.findByIdAndProvider(2L, other))
                .thenReturn(Optional.of(new ServiceOffering(other, "Other service", 30)));
        mvc.perform(get("/api/public/barber-devo/slots").param("serviceId", Long.toString(id))
                .param("date", DATE.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("status").value(404));
        verify(services).findByIdAndProvider(id, provider);
        verifyNoInteractions(availabilities);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?serviceId=1", "?date=2030-01-11", "?serviceId=abc&date=2030-01-11",
            "?serviceId=1&date=invalid", "?serviceId=1&date=2030-02-30",
            "?serviceId=0&date=2030-01-11", "?serviceId=-1&date=2030-01-11"})
    void rejectsMissingOrInvalidParameters(String query) throws Exception {
        mvc.perform(get("/api/public/barber-devo/slots" + query)).andExpect(status().isBadRequest());
        verifyNoInteractions(providers, services, availabilities);
    }

    @Test
    void doesNotMakeOtherPublicPathsOrCrudPublic() throws Exception {
        mvc.perform(get("/api/public/barber-devo/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/availabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
        verifyNoInteractions(providers, services, availabilities);
    }

    private void assertSlots(LocalDate date, String... expected) throws Exception {
        String body = mvc.perform(get("/api/public/barber-devo/slots")
                .param("serviceId", "1").param("date", date.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readValue(body, LocalTime[].class))
                .containsExactly(Arrays.stream(expected).map(LocalTime::parse).toArray(LocalTime[]::new));
        assertThat(body).doesNotContain("devo@example.com", "private-hash", "provider");
    }

    private Availability window(LocalDate date, String start, String end) {
        return new Availability(provider, date, LocalTime.parse(start), LocalTime.parse(end));
    }
}
