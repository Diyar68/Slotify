package dev.diyar68.slotify_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.entity.ServiceOffering;
import dev.diyar68.slotify_backend.exception.GlobalExceptionHandler;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.repository.ServiceOfferingRepository;
import dev.diyar68.slotify_backend.service.ServiceOfferingService;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ServiceOfferingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ServiceOfferingService.class})
@WithMockUser(username = "a@example.com", roles = "PROVIDER")
class ServiceOfferingTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean ServiceOfferingRepository offerings;
    @MockitoBean ProviderRepository providers;
    private Provider providerA;
    private Provider providerB;

    @BeforeEach
    void providers() {
        providerA = new Provider("Provider A", "a@example.com", "secret-hash-a", "provider-a");
        providerB = new Provider("Provider B", "b@example.com", "secret-hash-b", "provider-b");
        ReflectionTestUtils.setField(providerA, "id", 1L);
        ReflectionTestUtils.setField(providerB, "id", 2L);
        when(providers.findByEmail("a@example.com")).thenReturn(Optional.of(providerA));
        when(providers.findByEmail("b@example.com")).thenReturn(Optional.of(providerB));
    }

    @Test
    void createsForAuthenticatedOwnerRegardlessOfClientProviderId() throws Exception {
        when(offerings.save(any(ServiceOffering.class))).thenAnswer(invocation -> {
            ServiceOffering offering = invocation.getArgument(0);
            ReflectionTestUtils.setField(offering, "id", 10L);
            return offering;
        });
        mvc.perform(post("/api/services").with(csrf()).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("name", "Haarschnitt", "duration", 30, "providerId", 2))))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"id\":10,\"name\":\"Haarschnitt\",\"duration\":30}"))
                .andExpect(jsonPath("provider").doesNotExist())
                .andExpect(jsonPath("providerId").doesNotExist());
        ArgumentCaptor<ServiceOffering> saved = ArgumentCaptor.forClass(ServiceOffering.class);
        verify(offerings).save(saved.capture());
        assertThat(saved.getValue().getProvider()).isSameAs(providerA);
    }

    @Test
    void listsOnlyCurrentProvidersOfferings() throws Exception {
        when(offerings.findAllByProvider(providerA)).thenReturn(List.of(offering(10L, providerA)));
        when(offerings.findAllByProvider(providerB)).thenReturn(List.of(offering(20L, providerB)));
        String body = mvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":10,\"name\":\"Haarschnitt\",\"duration\":30}]"))
                .andExpect(jsonPath("$[0].provider").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        verify(offerings).findAllByProvider(providerA);
        verify(offerings, never()).findAllByProvider(providerB);
        assertThat(body).doesNotContain("a@example.com", "b@example.com", "secret-hash");
    }

    @Test
    void updatesOwnOffering() throws Exception {
        ServiceOffering own = offering(10L, providerA);
        when(offerings.findByIdAndProvider(10L, providerA)).thenReturn(Optional.of(own));
        when(offerings.save(own)).thenReturn(own);
        mvc.perform(put("/api/services/10").with(csrf()).contentType("application/json")
                .content("{\"name\":\"Bartpflege\",\"duration\":15}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":10,\"name\":\"Bartpflege\",\"duration\":15}"));
        assertThat(own.getName()).isEqualTo("Bartpflege");
        assertThat(own.getDuration()).isEqualTo(15);
        assertThat(own.getProvider()).isSameAs(providerA);
        verify(offerings).save(own);
    }

    @Test
    void deletesOwnOffering() throws Exception {
        ServiceOffering own = offering(10L, providerA);
        when(offerings.findByIdAndProvider(10L, providerA)).thenReturn(Optional.of(own));
        mvc.perform(delete("/api/services/10").with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(offerings).delete(own);
    }

    @ParameterizedTest
    @ValueSource(longs = {20, 999})
    void refusesForeignAndMissingOfferingsForUpdateAndDelete(long id) throws Exception {
        ServiceOffering foreign = offering(20L, providerB);
        when(offerings.findByIdAndProvider(20L, providerB)).thenReturn(Optional.of(foreign));
        mvc.perform(put("/api/services/{id}", id).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Changed\",\"duration\":15}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("status").value(404))
                .andExpect(jsonPath("message").value("Service offering not found"));
        mvc.perform(delete("/api/services/{id}", id).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("status").value(404));
        verify(offerings, times(2)).findByIdAndProvider(id, providerA);
        verify(offerings, never()).save(any());
        verify(offerings, never()).delete(any(ServiceOffering.class));
        assertThat(foreign.getName()).isEqualTo("Haarschnitt");
        assertThat(foreign.getDuration()).isEqualTo(30);
    }

    static Stream<String> invalidRequests() {
        return Stream.of(
                "{\"duration\":30}",
                "{\"name\":\"   \",\"duration\":30}",
                "{\"name\":\"" + "a".repeat(101) + "\",\"duration\":30}",
                "{\"name\":\"Haarschnitt\"}",
                "{\"name\":\"Haarschnitt\",\"duration\":null}",
                "{\"name\":\"Haarschnitt\",\"duration\":0}",
                "{\"name\":\"Haarschnitt\",\"duration\":-1}");
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validatesCreateAndUpdate(String body) throws Exception {
        mvc.perform(post("/api/services").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("status").value(400));
        mvc.perform(put("/api/services/10").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("status").value(400));
        verifyNoInteractions(offerings, providers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    @WithAnonymousUser
    void rejectsAnonymousRequestsEvenWithoutCsrf(String method) throws Exception {
        String path = method.equals("PUT") || method.equals("DELETE") ? "/api/services/10" : "/api/services";
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType("application/json")
                .content("{\"name\":\"Haarschnitt\",\"duration\":30}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
        verifyNoInteractions(offerings, providers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void requiresCsrfForWrites(String method) throws Exception {
        String path = method.equals("POST") ? "/api/services" : "/api/services/10";
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType("application/json")
                .content("{\"name\":\"Haarschnitt\",\"duration\":30}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(offerings, providers);
    }

    @Test
    void allowsReactPutPreflight() throws Exception {
        mvc.perform(options("/api/services/10").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private ServiceOffering offering(Long id, Provider provider) {
        ServiceOffering offering = new ServiceOffering(provider, "Haarschnitt", 30);
        ReflectionTestUtils.setField(offering, "id", id);
        return offering;
    }
}
