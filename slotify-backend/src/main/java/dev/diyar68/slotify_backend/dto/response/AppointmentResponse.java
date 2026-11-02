package dev.diyar68.slotify_backend.dto.response;

import java.time.LocalDateTime;
import dev.diyar68.slotify_backend.entity.AppointmentStatus;

public record AppointmentResponse(Long id, Long serviceId, LocalDateTime startTime,
        LocalDateTime endTime, AppointmentStatus status) {
}
