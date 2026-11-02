package dev.diyar68.slotify_backend.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import dev.diyar68.slotify_backend.dto.request.AvailabilityRequest;
import dev.diyar68.slotify_backend.dto.response.AvailabilityResponse;
import dev.diyar68.slotify_backend.service.AvailabilityService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/availabilities")
public class AvailabilityController {
    private final AvailabilityService availabilities;

    public AvailabilityController(AvailabilityService availabilities) {
        this.availabilities = availabilities;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityResponse create(Principal principal, @Valid @RequestBody AvailabilityRequest request) {
        return availabilities.create(principal.getName(), request);
    }

    @GetMapping
    public List<AvailabilityResponse> findAll(Principal principal) {
        return availabilities.findAll(principal.getName());
    }

    @PutMapping("/{id}")
    public AvailabilityResponse update(Principal principal, @PathVariable Long id,
            @Valid @RequestBody AvailabilityRequest request) {
        return availabilities.update(principal.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable Long id) {
        availabilities.delete(principal.getName(), id);
    }
}
