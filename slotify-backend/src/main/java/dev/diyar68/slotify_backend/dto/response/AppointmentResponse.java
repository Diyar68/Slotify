package dev.diyar68.slotify_backend.dto.response;

import dev.diyar68.slotify_backend.entity.AppointmentStatus;
import java.time.LocalDateTime;

public record AppointmentResponse(
    Long id,
    Long serviceId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    AppointmentStatus status
) {}
