package dev.diyar68.slotify_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.diyar68.slotify_backend.entity.Availability;
import dev.diyar68.slotify_backend.entity.Provider;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    List<Availability> findAllByProvider(Provider provider);
    List<Availability> findAllByProviderAndDate(Provider provider, LocalDate date);
    Optional<Availability> findByIdAndProvider(Long id, Provider provider);
}
