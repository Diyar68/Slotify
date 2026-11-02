package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import dev.diyar68.slotify_backend.config.SecurityConfig;
import dev.diyar68.slotify_backend.entity.Availability;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.repository.AvailabilityRepository;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.service.AvailabilityService;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AvailabilityController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AvailabilityService.class})
@WithMockUser(username = "a@example.com", roles = "PROVIDER")
class AvailabilityTests {
    private static final LocalDate DATE = LocalDate.of(2030, 1, 11);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AvailabilityRepository availabilities;
    @MockitoBean ProviderRepository providers;
    @MockitoBean Clock clock;
    private Provider providerA;
    private Provider providerB;

    @BeforeEach
    void setup() {
        // 09:00 in Berlin; the JVM's default timezone does not affect these tests.
        when(clock.instant()).thenReturn(Instant.parse("2030-01-10T08:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Berlin"));
        providerA = new Provider("A", "a@example.com", "hash-a", "provider-a");
        providerB = new Provider("B", "b@example.com", "hash-b", "provider-b");
        ReflectionTestUtils.setField(providerA, "id", 1L);
        ReflectionTestUtils.setField(providerB, "id", 2L);
        when(providers.findByEmail("a@example.com")).thenReturn(Optional.of(providerA));
        when(providers.findByEmail("b@example.com")).thenReturn(Optional.of(providerB));
        when(providers.findByEmailForUpdate("a@example.com")).thenReturn(Optional.of(providerA));
        when(providers.findByEmailForUpdate("b@example.com")).thenReturn(Optional.of(providerB));
    }

    private void saveSucceeds() {
        when(availabilities.save(any(Availability.class))).thenAnswer(invocation -> {
            Availability availability = invocation.getArgument(0);
            if (availability.getId() == null) ReflectionTestUtils.setField(availability, "id", 10L);
            return availability;
        });
    }

    @Test
    void createsForAuthenticatedProviderAndIgnoresClientProviderId() throws Exception {
        saveSucceeds();
        String body = mvc.perform(post("/api/availabilities").with(csrf()).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("date", DATE.toString(),
                        "startTime", "09:00", "endTime", "12:00", "providerId", 2))))
                .andExpect(status().isCreated()).andExpect(jsonPath("id").value(10))
                .andExpect(jsonPath("date").value(DATE.toString()))
                .andExpect(jsonPath("provider").doesNotExist())
                .andExpect(jsonPath("providerId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        ArgumentCaptor<Availability> saved = ArgumentCaptor.forClass(Availability.class);
        verify(availabilities).save(saved.capture());
        assertThat(saved.getValue().getProvider()).isSameAs(providerA);
        assertThat(saved.getValue().getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.getValue().getEndTime()).isEqualTo(LocalTime.NOON);
        assertThat(body).doesNotContain("a@example.com", "hash-a");
        verify(providers).findByEmailForUpdate("a@example.com");
    }

    @Test
    void listsOnlyOwnAvailabilities() throws Exception {
        when(availabilities.findAllByProvider(providerA)).thenReturn(List.of(window(10L, providerA, "09:00", "12:00")));
        when(availabilities.findAllByProvider(providerB)).thenReturn(List.of(window(20L, providerB, "09:00", "12:00")));
        mvc.perform(get("/api/availabilities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].provider").doesNotExist());
        verify(availabilities).findAllByProvider(providerA);
        verify(availabilities, never()).findAllByProvider(providerB);
    }

    @Test
    void updatesOwnWindowWithoutConflictingWithItself() throws Exception {
        Availability own = window(10L, providerA, "09:00", "12:00");
        when(availabilities.findByIdAndProvider(10L, providerA)).thenReturn(Optional.of(own));
        when(availabilities.findAllByProviderAndDate(providerA, DATE)).thenReturn(List.of(own));
        saveSucceeds();
        mvc.perform(put("/api/availabilities/10").with(csrf()).contentType("application/json")
                .content(payload(DATE, "10:00", "11:00")))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(10));
        assertThat(own.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(own.getEndTime()).isEqualTo(LocalTime.of(11, 0));
        verify(availabilities).save(own);
    }

    @Test
    void rejectsOverlapWhenUpdatingAndLeavesEntityUnchanged() throws Exception {
        Availability own = window(10L, providerA, "15:00", "18:00");
        when(availabilities.findByIdAndProvider(10L, providerA)).thenReturn(Optional.of(own));
        when(availabilities.findAllByProviderAndDate(providerA, DATE))
                .thenReturn(List.of(window(20L, providerA, "09:00", "12:00"), own));
        mvc.perform(put("/api/availabilities/10").with(csrf()).contentType("application/json")
                .content(payload(DATE, "11:00", "14:00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("status").value(409));
        assertThat(own.getStartTime()).isEqualTo(LocalTime.of(15, 0));
        verify(availabilities, never()).save(any());
    }

    @Test
    void deletesOwnWindow() throws Exception {
        Availability own = window(10L, providerA, "09:00", "12:00");
        when(availabilities.findByIdAndProvider(10L, providerA)).thenReturn(Optional.of(own));
        mvc.perform(delete("/api/availabilities/10").with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(availabilities).delete(own);
    }

    @ParameterizedTest
    @CsvSource({"11:00,14:00", "09:30,10:30", "08:00,13:00", "09:00,12:00", "08:00,09:30"})
    void rejectsOverlappingWindows(String start, String end) throws Exception {
        when(availabilities.findAllByProviderAndDate(providerA, DATE))
                .thenReturn(List.of(window(20L, providerA, "09:00", "12:00")));
        mvc.perform(post("/api/availabilities").with(csrf()).contentType("application/json")
                .content(payload(DATE, start, end)))
                .andExpect(status().isConflict()).andExpect(jsonPath("status").value(409));
        verify(availabilities, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"08:00,09:00", "12:00,15:00", "15:00,18:00"})
    void acceptsAdjacentAndSeparatedWindows(String start, String end) throws Exception {
        when(availabilities.findAllByProviderAndDate(providerA, DATE))
                .thenReturn(List.of(window(20L, providerA, "09:00", "12:00")));
        saveSucceeds();
        mvc.perform(post("/api/availabilities").with(csrf()).contentType("application/json")
                .content(payload(DATE, start, end))).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "b@example.com", roles = "PROVIDER")
    void allowsSameWindowForDifferentProvider() throws Exception {
        when(availabilities.findAllByProviderAndDate(providerA, DATE))
                .thenReturn(List.of(window(20L, providerA, "09:00", "12:00")));
        saveSucceeds();
        mvc.perform(post("/api/availabilities").with(csrf()).contentType("application/json")
                .content(payload(DATE, "09:00", "12:00"))).andExpect(status().isCreated());
        verify(availabilities).findAllByProviderAndDate(providerB, DATE);
        verify(availabilities, never()).findAllByProviderAndDate(providerA, DATE);
    }

    @ParameterizedTest
    @ValueSource(longs = {20, 999})
    void rejectsForeignAndMissingIds(long id) throws Exception {
        Availability foreign = window(20L, providerB, "09:00", "12:00");
        when(availabilities.findByIdAndProvider(20L, providerB)).thenReturn(Optional.of(foreign));
        mvc.perform(put("/api/availabilities/{id}", id).with(csrf()).contentType("application/json")
                .content(payload(DATE, "10:00", "11:00")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("status").value(404));
        mvc.perform(delete("/api/availabilities/{id}", id).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("status").value(404));
        verify(availabilities, times(2)).findByIdAndProvider(id, providerA);
        verify(availabilities, never()).save(any());
        verify(availabilities, never()).delete(any(Availability.class));
        assertThat(foreign.getStartTime()).isEqualTo(LocalTime.of(9, 0));
    }

    static Stream<String> invalidRequests() {
        return Stream.of("{}", "{\"date\":null,\"startTime\":\"09:00\",\"endTime\":\"12:00\"}",
                "{\"date\":\"2030-01-11\",\"endTime\":\"12:00\"}",
                "{\"date\":\"2030-01-11\",\"startTime\":\"09:00\"}",
                "{\"date\":\"2030-01-11\",\"startTime\":\"12:00\",\"endTime\":\"09:00\"}",
                "{\"date\":\"2030-01-11\",\"startTime\":\"09:00\",\"endTime\":\"09:00\"}",
                "{\"date\":\"invalid\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}");
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidCreateAndUpdate(String body) throws Exception {
        mvc.perform(post("/api/availabilities").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/availabilities/10").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(availabilities, providers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-09", "2030-01-10"})
    void rejectsPastStartUsingApplicationTimezone(String date) throws Exception {
        // 08:30 today is already past in Berlin, though still future relative to UTC 08:00.
        mvc.perform(post("/api/availabilities").with(csrf()).contentType("application/json")
                .content(payload(LocalDate.parse(date), "08:30", "12:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("status").value(400));
        verify(availabilities, never()).save(any());
    }

    @Test
    void allowsWindowStartingNow() throws Exception {
        saveSucceeds();
        mvc.perform(post("/api/availabilities").with(csrf()).contentType("application/json")
                .content(payload(LocalDate.of(2030, 1, 10), "09:00", "12:00"))).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    @WithAnonymousUser
    void rejectsAnonymousRequests(String method) throws Exception {
        String path = method.equals("PUT") || method.equals("DELETE") ? "/api/availabilities/10" : "/api/availabilities";
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType("application/json")
                .content(payload(DATE, "09:00", "12:00")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
        verifyNoInteractions(availabilities, providers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void requiresCsrfForWrites(String method) throws Exception {
        String path = method.equals("POST") ? "/api/availabilities" : "/api/availabilities/10";
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType("application/json")
                .content(payload(DATE, "09:00", "12:00"))).andExpect(status().isForbidden());
        verifyNoInteractions(availabilities, providers);
    }

    private Availability window(Long id, Provider provider, String start, String end) {
        Availability availability = new Availability(provider, DATE, LocalTime.parse(start), LocalTime.parse(end));
        ReflectionTestUtils.setField(availability, "id", id);
        return availability;
    }

    private String payload(LocalDate date, String start, String end) {
        return objectMapper.writeValueAsString(Map.of("date", date.toString(), "startTime", start, "endTime", end));
    }
}
