package dev.diyar68.slotify_backend.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Invalid registration token")
    String token,
    @NotBlank @Size(min = 8) String password,
    @Size(max = 100)
    @Pattern(
        regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$",
        message = "Slug may only contain lowercase letters, numbers and single hyphens"
    )
    String bookingSlug
) {
    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }

    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
