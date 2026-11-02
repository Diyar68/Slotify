package dev.diyar68.slotify_backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.diyar68.slotify_backend.entity.Availability;
import dev.diyar68.slotify_backend.entity.Appointment;
import dev.diyar68.slotify_backend.entity.AppointmentStatus;
import dev.diyar68.slotify_backend.entity.Provider;
import dev.diyar68.slotify_backend.entity.ServiceOffering;
import dev.diyar68.slotify_backend.exception.BadRequestException;
import dev.diyar68.slotify_backend.exception.NotFoundException;
import dev.diyar68.slotify_backend.repository.AvailabilityRepository;
import dev.diyar68.slotify_backend.repository.AppointmentRepository;
import dev.diyar68.slotify_backend.repository.ProviderRepository;
import dev.diyar68.slotify_backend.repository.ServiceOfferingRepository;

@Service
@Transactional(readOnly = true)
public class SlotCalculator {
    private final ProviderRepository providers;
    private final ServiceOfferingRepository services;
    private final AvailabilityRepository availabilities;
    private final Clock clock;
    private final AppointmentRepository appointments;

    public SlotCalculator(ProviderRepository providers, ServiceOfferingRepository services,
            AvailabilityRepository availabilities, Clock clock, AppointmentRepository appointments) {
        this.providers = providers;
        this.services = services;
        this.availabilities = availabilities;
        this.clock = clock;
        this.appointments = appointments;
    }

    public List<LocalTime> findSlots(String bookingSlug, long serviceId, LocalDate date) {
        if (serviceId <= 0) {
            throw new BadRequestException("Service ID must be positive");
        }
        Provider provider = providers.findByBookingSlug(bookingSlug)
                .orElseThrow(() -> new NotFoundException("Provider not found"));
        ServiceOffering service = services.findByIdAndProvider(serviceId, provider)
                .orElseThrow(() -> new NotFoundException("Service offering not found"));
        return findSlots(provider, service, date);
    }

    // Also used after acquiring the provider lock during booking.
    public List<LocalTime> findSlots(Provider provider, ServiceOffering service, LocalDate date) {
        int duration = service.getDuration();
        if (duration <= 0) {
            throw new BadRequestException("Service duration must be positive");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (date.isBefore(now.toLocalDate())) {
            return List.of();
        }
        List<Appointment> booked = appointments.findOverlapping(provider, AppointmentStatus.BOOKED,
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        return calculateSlots(availabilities.findAllByProviderAndDate(provider, date), date, duration, now, booked);
    }

    private List<LocalTime> calculateSlots(List<Availability> windows, LocalDate date,
            int duration, LocalDateTime now, List<Appointment> booked) {
        // A TreeSet keeps the result chronological and removes duplicate start times.
        TreeSet<LocalTime> slots = new TreeSet<>();
        for (Availability window : windows) {
            LocalTime start = window.getStartTime();
            LocalTime end = window.getEndTime();
            // Check remaining time before adding minutes: LocalTime must never wrap past midnight.
            while (Duration.between(start, end).toMinutes() >= duration) {
                LocalDateTime slotStart = LocalDateTime.of(date, start);
                if (slotStart.isAfter(now) && !overlapsBooking(slotStart, slotStart.plusMinutes(duration), booked)) {
                    slots.add(start);
                }
                start = start.plusMinutes(duration);
            }
        }
        return new ArrayList<>(slots);
    }

    private boolean overlapsBooking(LocalDateTime start, LocalDateTime end, List<Appointment> booked) {
        for (Appointment appointment : booked) {
            if (appointment.getStatus() == AppointmentStatus.BOOKED
                    && appointment.getStartTime().isBefore(end) && appointment.getEndTime().isAfter(start)) {
                return true;
            }
        }
        return false;
    }
}
