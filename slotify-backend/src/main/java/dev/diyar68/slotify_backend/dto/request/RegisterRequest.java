package dev.diyar68.slotify_backend.dto.request;

import java.nio.charset.StandardCharsets;

import dev.diyar68.slotify_backend.entity.Provider;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8) String password,
        @Size(max = 100) @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug may only contain lowercase letters, numbers and single hyphens") String bookingSlug) {

    public RegisterRequest {
        email = Provider.normalizeEmail(email);
    }

    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
