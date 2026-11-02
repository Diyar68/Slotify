package dev.diyar68.slotify_backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import dev.diyar68.slotify_backend.entity.Provider;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByEmail(String email);

    Optional<Provider> findByBookingSlug(String bookingSlug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Provider p where p.bookingSlug = :slug")
    Optional<Provider> findByBookingSlugForUpdate(@Param("slug") String bookingSlug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Provider p where p.email = :email")
    Optional<Provider> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByBookingSlug(String bookingSlug);
}
