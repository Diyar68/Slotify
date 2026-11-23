package dev.diyar68.slotify_backend.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record AppointmentRequest(
    @NotNull @Positive Long serviceId,
    @NotBlank @Size(max = 100) String customerName,
    @NotBlank @Email @Size(max = 150) String customerEmail,
    @NotBlank @Size(max = 30) String customerPhone,
    @NotNull LocalDateTime startTime
) {}
