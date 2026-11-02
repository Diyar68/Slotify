package dev.diyar68.slotify_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.entity.ServiceOffering;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    List<ServiceOffering> findAllByProvider(Provider provider);

    Optional<ServiceOffering> findByIdAndProvider(Long id, Provider provider);
}
