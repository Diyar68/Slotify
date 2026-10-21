package dev.diyar68.slotify_backend.dto.response;

public record ErrorResponse(
        int status,
        String message) {
}
