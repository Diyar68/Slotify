package dev.diyar68.slotify_backend.dto.response;

import java.time.LocalDateTime;

import dev.diyar68.slotify_backend.entity.AppointmentStatus;

// Customer details are only exposed on authenticated, owner-scoped endpoints.
public record ProviderAppointmentResponse(Long id, ServiceOfferingResponse serviceOffering,
        String customerName, String customerEmail, String customerPhone,
        LocalDateTime startTime, LocalDateTime endTime, AppointmentStatus status) {
}
