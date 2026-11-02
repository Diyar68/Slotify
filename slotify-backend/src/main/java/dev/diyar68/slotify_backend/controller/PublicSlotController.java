package dev.diyar68.slotify_backend.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.diyar68.slotify_backend.service.SlotCalculator;

@RestController
public class PublicSlotController {
    private final SlotCalculator slots;

    public PublicSlotController(SlotCalculator slots) {
        this.slots = slots;
    }

    @GetMapping("/api/public/{bookingSlug}/slots")
    public List<LocalTime> findSlots(@PathVariable String bookingSlug, @RequestParam long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slots.findSlots(bookingSlug, serviceId, date);
    }
}
