package dev.diyar68.slotify_backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
                @NotBlank @Size(max = 100) String name,
                @NotBlank @Email @Size(max = 150) String email,
                @NotBlank @Size(min = 8, max = 150) String password,
                @Size(min = 3, max = 100) @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug may only contain lowercase letters, numbers and single hyphens") String bookingSlug) {
}
