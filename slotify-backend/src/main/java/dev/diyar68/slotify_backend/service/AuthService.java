package dev.diyar68.slotify_backend.service;

import java.security.SecureRandom;
import java.util.Locale;

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

        String finalBookingSlug = request.bookingSlug();

        if (finalBookingSlug == null || finalBookingSlug.isEmpty()) {

            int maxAttempts = 10;
            int attempts = 0;
            boolean slugExists;

            do {
                finalBookingSlug = generateSlug(request.name());
                attempts++;
                slugExists = providerRepository.existsByBookingSlug(finalBookingSlug);

            } while (slugExists && attempts < maxAttempts);

            if (slugExists) {
                throw new IllegalStateException("Could not generate a unique booking slug");
            }

        } else {
            if (providerRepository.existsByBookingSlug(finalBookingSlug)) {
                throw new IllegalArgumentException("This Slug already exists, take another one!");
            }
        }

        Provider providerEntity = new Provider(request.name(), request.email(), passwordHash, finalBookingSlug);

        Provider savedEntity = providerRepository.save(providerEntity);

        return providerMapper.toResponseDto(savedEntity);
    }

    private String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            name = "user";
        }

        String baseSlug = name.toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (baseSlug.isBlank()) {
            baseSlug = "user";
        }

        if (baseSlug.length() > 93) {
            baseSlug = baseSlug.substring(0, 93);
            baseSlug = baseSlug.replaceAll("-+$", "");
        }

        String characters = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder suffix = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(characters.length());
            suffix.append(characters.charAt(index));
        }

        return baseSlug + "-" + suffix;
    }

}
