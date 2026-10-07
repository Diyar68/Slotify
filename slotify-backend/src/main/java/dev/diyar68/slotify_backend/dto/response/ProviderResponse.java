package dev.diyar68.slotify_backend.dto.response;

public record ProviderResponse(
        String name,
        String email,
        String bookingSlug) {
}
