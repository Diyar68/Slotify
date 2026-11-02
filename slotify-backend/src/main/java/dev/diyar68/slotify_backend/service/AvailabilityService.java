package dev.diyar68.slotify_backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.diyar68.slotify_backend.dto.request.AvailabilityRequest;
import dev.diyar68.slotify_backend.dto.response.AvailabilityResponse;
import dev.diyar68.slotify_backend.entity.Availability;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.exception.BadRequestException;
import dev.diyar68.slotify_backend.exception.ConflictException;
import dev.diyar68.slotify_backend.exception.NotFoundException;
import dev.diyar68.slotify_backend.repository.AvailabilityRepository;
import dev.diyar68.slotify_backend.repository.ProviderRepository;

@Service
@Transactional
public class AvailabilityService {
    private final AvailabilityRepository availabilities;
    private final ProviderRepository providers;
    private final Clock clock;

    public AvailabilityService(AvailabilityRepository availabilities, ProviderRepository providers, Clock clock) {
        this.availabilities = availabilities;
        this.providers = providers;
        this.clock = clock;
    }

    public AvailabilityResponse create(String email, AvailabilityRequest request) {
        Provider provider = lockedProvider(email);
        validateWindow(provider, request, null);
        return toResponse(availabilities.save(new Availability(provider, request.date(),
                request.startTime(), request.endTime())));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> findAll(String email) {
        return availabilities.findAllByProvider(currentProvider(email)).stream().map(this::toResponse).toList();
    }

    public AvailabilityResponse update(String email, Long id, AvailabilityRequest request) {
        Provider provider = lockedProvider(email);
        Availability availability = ownAvailability(provider, id);
        validateWindow(provider, request, id);
        availability.setDate(request.date());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        return toResponse(availabilities.save(availability));
    }

    public void delete(String email, Long id) {
        availabilities.delete(ownAvailability(lockedProvider(email), id));
    }

    private void validateWindow(Provider provider, AvailabilityRequest request, Long excludedId) {
        if (LocalDateTime.of(request.date(), request.startTime()).isBefore(LocalDateTime.now(clock))) {
            throw new BadRequestException("Availability must not start in the past");
        }
        for (Availability existing : availabilities.findAllByProviderAndDate(provider, request.date())) {
            if (excludedId != null && excludedId.equals(existing.getId())) {
                continue;
            }
            // Strict comparisons allow adjacent windows, e.g. 09:00-12:00 and 12:00-15:00.
            if (request.startTime().isBefore(existing.getEndTime())
                    && request.endTime().isAfter(existing.getStartTime())) {
                throw new ConflictException("Availability overlaps an existing time window");
            }
        }
    }

    private Availability ownAvailability(Provider provider, Long id) {
        return availabilities.findByIdAndProvider(id, provider)
                .orElseThrow(() -> new NotFoundException("Availability not found"));
    }

    private Provider currentProvider(String email) {
        return providers.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
    }

    private Provider lockedProvider(String email) {
        // Serialize overlap checks and writes for this provider, including when no windows exist yet.
        return providers.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
    }

    private AvailabilityResponse toResponse(Availability availability) {
        return new AvailabilityResponse(availability.getId(), availability.getDate(),
                availability.getStartTime(), availability.getEndTime());
    }
}
