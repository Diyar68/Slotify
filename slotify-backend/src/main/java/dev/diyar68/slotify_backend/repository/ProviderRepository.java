package dev.diyar68.slotify_backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import dev.diyar68.slotify_backend.entity.Provider;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByBookingSlug(String bookingSlug);
}
