package dev.diyar68.slotify_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import dev.diyar68.slotify_backend.entity.*;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findAllByProviderOrderByStartTimeAscIdAsc(Provider provider);

    Optional<Appointment> findByIdAndProvider(Long id, Provider provider);

    @Query("""
            select a from Appointment a
            where a.provider = :provider and a.status = :status
            and a.startTime < :end and a.endTime > :start
            """)
    List<Appointment> findOverlapping(@Param("provider") Provider provider,
            @Param("status") AppointmentStatus status, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
