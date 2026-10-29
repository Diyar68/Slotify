package dev.diyar68.slotify_backend.service;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.diyar68.slotify_backend.dto.request.LoginRequest;
import dev.diyar68.slotify_backend.dto.request.RegisterRequest;
import dev.diyar68.slotify_backend.dto.response.ProviderResponse;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.exception.ConflictException;
import dev.diyar68.slotify_backend.mapper.ProviderMapper;
import dev.diyar68.slotify_backend.repository.ProviderRepository;

@Service
public class AuthService {
    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthService(ProviderRepository providerRepository, ProviderMapper providerMapper,
            PasswordEncoder passwordEncoder) {
        this.providerRepository = providerRepository;
        this.providerMapper = providerMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("unused-login-password");
    }

    @Transactional
    public ProviderResponse register(RegisterRequest request) {
        if (providerRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already exists");
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
                throw new ConflictException("Could not generate a unique booking slug");
            }

        } else {
            if (providerRepository.existsByBookingSlug(finalBookingSlug)) {
                throw new ConflictException("This Slug already exists, take another one!");
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

    @Transactional(readOnly = true)
    public Authentication login(LoginRequest request) {
        Provider provider = providerRepository.findByEmail(request.email()).orElse(null);
        String hash = provider == null ? dummyPasswordHash : provider.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (provider == null || !passwordMatches || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return UsernamePasswordAuthenticationToken.authenticated(provider.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
    }

    @Transactional(readOnly = true)
    public ProviderResponse currentProvider(String email) {
        Provider provider = providerRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return providerMapper.toResponseDto(provider);
    }

}
