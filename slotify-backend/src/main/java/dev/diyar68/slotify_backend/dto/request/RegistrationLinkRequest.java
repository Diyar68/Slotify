package dev.diyar68.slotify_backend.dto.request;

import dev.diyar68.slotify_backend.entity.Provider;
import jakarta.validation.constraints.*;

public record RegistrationLinkRequest(@NotBlank @Email @Size(max = 150) String email) {
    public RegistrationLinkRequest {
        email = Provider.normalizeEmail(email);
    }
}
