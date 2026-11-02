package dev.diyar68.slotify_backend.controller;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import dev.diyar68.slotify_backend.dto.request.AppointmentRequest;
import dev.diyar68.slotify_backend.dto.response.AppointmentResponse;
import dev.diyar68.slotify_backend.dto.response.ProviderAppointmentResponse;
import dev.diyar68.slotify_backend.service.AppointmentService;
import jakarta.validation.Valid;

@RestController
public class AppointmentController {
    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @PostMapping("/api/public/{bookingSlug}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@PathVariable String bookingSlug, @Valid @RequestBody AppointmentRequest request) {
        return appointments.book(bookingSlug, request);
    }

    @GetMapping("/api/appointments")
    public List<ProviderAppointmentResponse> findAll(Principal principal) {
        return appointments.findAll(principal.getName());
    }

    @GetMapping("/api/appointments/{id}")
    public ProviderAppointmentResponse findOne(Principal principal, @PathVariable Long id) {
        return appointments.findOne(principal.getName(), id);
    }

    @PatchMapping("/api/appointments/{id}/cancel")
    public ProviderAppointmentResponse cancel(Principal principal, @PathVariable Long id) {
        return appointments.cancel(principal.getName(), id);
    }
}
