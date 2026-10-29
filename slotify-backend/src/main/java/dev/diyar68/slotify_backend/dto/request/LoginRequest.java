package dev.diyar68.slotify_backend.dto.request;

import dev.diyar68.slotify_backend.entity.Provider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password) {

    public LoginRequest {
        email = Provider.normalizeEmail(email);
    }
}
