package dev.diyar68.slotify_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ServiceOfferingRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive Integer duration) {
}
