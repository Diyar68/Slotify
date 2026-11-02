package dev.diyar68.slotify_backend.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityResponse(Long id, LocalDate date, LocalTime startTime, LocalTime endTime) {
}
