package dev.diyar68.slotify_backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.diyar68.slotify_backend.dto.request.AppointmentRequest;
import dev.diyar68.slotify_backend.dto.response.AppointmentResponse;
import dev.diyar68.slotify_backend.dto.response.ProviderAppointmentResponse;
import dev.diyar68.slotify_backend.dto.response.ServiceOfferingResponse;
import dev.diyar68.slotify_backend.entity.*;
import dev.diyar68.slotify_backend.exception.*;
import dev.diyar68.slotify_backend.repository.*;

@Service
public class AppointmentService {
    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;
    private final AppointmentRepository appointments;
    private final SlotCalculator slots;
    private final Clock clock;

    public AppointmentService(ProviderRepository providers, ServiceOfferingRepository services,
            AppointmentRepository appointments, SlotCalculator slots, Clock clock) {
        this.providers = providers;
        this.services = services;
        this.appointments = appointments;
        this.slots = slots;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResponse book(String bookingSlug, AppointmentRequest request) {
        // Lock first. Recheck windows and bookings in this transaction, never trust a previous slot response.
        Provider provider = providers.findByBookingSlugForUpdate(bookingSlug)
                .orElseThrow(() -> new NotFoundException("Provider not found"));
        ServiceOffering service = services.findByIdAndProvider(request.serviceId(), provider)
                .orElseThrow(() -> new NotFoundException("Service offering not found"));
        LocalDateTime start = request.startTime();
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new BadRequestException("Appointment must start in the future");
        }
        if (!slots.findSlots(provider, service, start.toLocalDate()).contains(start.toLocalTime())) {
            throw new ConflictException("Appointment is no longer available");
        }
        Appointment appointment = appointments.save(new Appointment(provider, service, request.customerName(),
                request.customerEmail(), request.customerPhone(), start, start.plusMinutes(service.getDuration())));
        return new AppointmentResponse(appointment.getId(), service.getId(), appointment.getStartTime(),
                appointment.getEndTime(), appointment.getStatus());
    }

    @Transactional(readOnly = true)
    public List<ProviderAppointmentResponse> findAll(String email) {
        return appointments.findAllByProviderOrderByStartTimeAscIdAsc(currentProvider(email))
                .stream().map(this::toProviderResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProviderAppointmentResponse findOne(String email, Long id) {
        return toProviderResponse(ownAppointment(currentProvider(email), id));
    }

    @Transactional
    public ProviderAppointmentResponse cancel(String email, Long id) {
        // Use the same provider lock as booking so cancellation and rebooking are serialized.
        Provider provider = providers.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
        Appointment appointment = ownAppointment(provider, id);
        if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointments.save(appointment);
        }
        return toProviderResponse(appointment);
    }

    private Provider currentProvider(String email) {
        return providers.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
    }

    private Appointment ownAppointment(Provider provider, Long id) {
        return appointments.findByIdAndProvider(id, provider)
                .orElseThrow(() -> new NotFoundException("Appointment not found"));
    }

    private ProviderAppointmentResponse toProviderResponse(Appointment appointment) {
        ServiceOffering service = appointment.getServiceOffering();
        return new ProviderAppointmentResponse(appointment.getId(),
                new ServiceOfferingResponse(service.getId(), service.getName(), service.getDuration()),
                appointment.getCustomerName(), appointment.getCustomerEmail(), appointment.getCustomerPhone(),
                appointment.getStartTime(), appointment.getEndTime(), appointment.getStatus());
    }
}
