package dev.diyar68.slotify_backend.mapper;

import org.springframework.stereotype.Component;

import dev.diyar68.slotify_backend.dto.request.RegisterRequest;
import dev.diyar68.slotify_backend.dto.response.ProviderResponse;
import dev.diyar68.slotify_backend.entity.Provider;

@Component
public class ProviderMapper {

    public ProviderResponse toResponseDto(Provider entity) {
        if (entity == null) {
            return null;
        }

        return new ProviderResponse(
                entity.getName(),
                entity.getEmail(),
                entity.getBookingSlug());
    }

    /*
     * public Provider toEntity(RegisterRequest request, String password, String
     * bookingSlug) {
     * if (request == null) {
     * return null;
     * }
     * 
     * return new Provider(
     * request.name(),
     * request.email(),
     * request.password(),
     * request.bookingSlug());
     * }
     */

    // erst später schauen ob ich einen toEntity Mapper brauche weil wir noch nicht
    // so ganz wissen ob sich die Eingabe ständig gleich wiederholt.
}
