package dev.diyar68.slotify_backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.diyar68.slotify_backend.dto.request.RegisterRequest;
import dev.diyar68.slotify_backend.dto.response.ProviderResponse;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.mapper.ProviderMapper;
import dev.diyar68.slotify_backend.repository.ProviderRepository;

@Service
public class AuthService {
    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(ProviderRepository providerRepository, ProviderMapper providerMapper,
            PasswordEncoder passwordEncoder) {
        this.providerRepository = providerRepository;
        this.providerMapper = providerMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ProviderResponse register(RegisterRequest request) {
        if (providerRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        String finalBookingSlug = request.bookingSlug().isEmpty() ? generateSlug() : request.bookingSlug(); // noch
                                                                                                            // nicht
                                                                                                            // ganz
                                                                                                            // fertig,
                                                                                                            // überlegen
                                                                                                            // was
                                                                                                            // machen
                                                                                                            // wenn null
                                                                                                            // oder leer

        Provider providerEntity = new Provider(request.name(), request.email(), passwordHash, finalBookingSlug);
    }

    private static String generateSlug(String name) {
        // Hilfsmethode fertig machen
    }

}
