package dev.diyar68.slotify_backend.repository;

import dev.diyar68.slotify_backend.entity.RegistrationToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RegistrationTokenRepository extends JpaRepository<RegistrationToken, Long> {
    Optional<RegistrationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RegistrationToken t where t.tokenHash = :hash")
    Optional<RegistrationToken> findByTokenHashForUpdate(@Param("hash") String hash);
}
