package dev.diyar68.slotify_backend.service;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.diyar68.slotify_backend.dto.request.ServiceOfferingRequest;
import dev.diyar68.slotify_backend.dto.response.ServiceOfferingResponse;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.entity.ServiceOffering;
import dev.diyar68.slotify_backend.exception.NotFoundException;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.repository.ServiceOfferingRepository;

@Service
@Transactional
public class ServiceOfferingService {
    private final ServiceOfferingRepository offerings;
    private final ProviderRepository providers;

    public ServiceOfferingService(ServiceOfferingRepository offerings, ProviderRepository providers) {
        this.offerings = offerings;
        this.providers = providers;
    }

    public ServiceOfferingResponse create(String email, ServiceOfferingRequest request) {
        Provider provider = currentProvider(email);
        ServiceOffering offering = new ServiceOffering(provider, request.name(), request.duration());
        return toResponse(offerings.save(offering));
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> findAll(String email) {
        return offerings.findAllByProvider(currentProvider(email)).stream().map(this::toResponse).toList();
    }

    public ServiceOfferingResponse update(String email, Long id, ServiceOfferingRequest request) {
        ServiceOffering offering = ownOffering(email, id);
        offering.setName(request.name());
        offering.setDuration(request.duration());
        return toResponse(offerings.save(offering));
    }

    public void delete(String email, Long id) {
        offerings.delete(ownOffering(email, id));
    }

    private ServiceOffering ownOffering(String email, Long id) {
        // Scope the lookup to the owner; foreign and missing IDs both return 404.
        return offerings.findByIdAndProvider(id, currentProvider(email))
                .orElseThrow(() -> new NotFoundException("Service offering not found"));
    }

    private Provider currentProvider(String email) {
        return providers.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
    }

    private ServiceOfferingResponse toResponse(ServiceOffering offering) {
        return new ServiceOfferingResponse(offering.getId(), offering.getName(), offering.getDuration());
    }
}
