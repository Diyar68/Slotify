package dev.diyar68.slotify_backend.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.diyar68.slotify_backend.dto.request.ServiceOfferingRequest;
import dev.diyar68.slotify_backend.dto.response.ServiceOfferingResponse;
import dev.diyar68.slotify_backend.service.ServiceOfferingService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/services")
public class ServiceOfferingController {
    private final ServiceOfferingService services;

    public ServiceOfferingController(ServiceOfferingService services) {
        this.services = services;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOfferingResponse create(Principal principal, @Valid @RequestBody ServiceOfferingRequest request) {
        return services.create(principal.getName(), request);
    }

    @GetMapping
    public List<ServiceOfferingResponse> findAll(Principal principal) {
        return services.findAll(principal.getName());
    }

    @PutMapping("/{id}")
    public ServiceOfferingResponse update(Principal principal, @PathVariable Long id,
            @Valid @RequestBody ServiceOfferingRequest request) {
        return services.update(principal.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable Long id) {
        services.delete(principal.getName(), id);
    }
}
