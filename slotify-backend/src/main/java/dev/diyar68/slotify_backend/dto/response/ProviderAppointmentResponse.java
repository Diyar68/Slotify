package dev.diyar68.slotify_backend.dto.response;

import dev.diyar68.slotify_backend.entity.AppointmentStatus;
import java.time.LocalDateTime;

// Customer details are only exposed on authenticated, owner-scoped endpoints.
public record ProviderAppointmentResponse(
    Long id,
    ServiceOfferingResponse serviceOffering,
    String customerName,
    String customerEmail,
    String customerPhone,
    LocalDateTime startTime,
    LocalDateTime endTime,
    AppointmentStatus status
) {}
