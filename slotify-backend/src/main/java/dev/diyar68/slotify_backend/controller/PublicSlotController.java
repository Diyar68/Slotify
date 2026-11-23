package dev.diyar68.slotify_backend.controller;

import dev.diyar68.slotify_backend.dto.response.PublicProviderResponse;
import dev.diyar68.slotify_backend.service.ServiceOfferingService;
import dev.diyar68.slotify_backend.service.SlotCalculator;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicSlotController {

    private final SlotCalculator slots;
    private final ServiceOfferingService services;

    public PublicSlotController(SlotCalculator slots, ServiceOfferingService services) {
        this.slots = slots;
        this.services = services;
    }

    @GetMapping("/api/public/{bookingSlug}")
    public PublicProviderResponse provider(@PathVariable String bookingSlug) {
        return services.findPublicProvider(bookingSlug);
    }

    @GetMapping("/api/public/{bookingSlug}/slots")
    public List<LocalTime> findSlots(
        @PathVariable String bookingSlug,
        @RequestParam long serviceId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return slots.findSlots(bookingSlug, serviceId, date);
    }
}
