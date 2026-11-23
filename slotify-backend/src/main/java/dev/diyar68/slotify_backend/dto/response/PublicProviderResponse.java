package dev.diyar68.slotify_backend.dto.response;

import java.util.List;

public record PublicProviderResponse(String name, List<ServiceOfferingResponse> services) {}
