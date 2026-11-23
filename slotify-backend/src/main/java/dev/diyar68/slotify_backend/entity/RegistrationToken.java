package dev.diyar68.slotify_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "registration_tokens")
public class RegistrationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RegistrationToken() {}

    public RegistrationToken(String email, String tokenHash, Instant expiresAt) {
        this.email = Provider.normalizeEmail(email);
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void markUsed(Instant time) {
        this.usedAt = time;
    }
}
