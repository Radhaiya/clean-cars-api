package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeRequest;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import com.example.cleancarsapi.repository.BikeModelRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Shared check that a bike's customer / brand / model all belong to the caller's org. */
@Component
@RequiredArgsConstructor
public class BikeReferenceValidator {

    private final CustomerRepository customers;
    private final BikeBrandRepository brands;
    private final BikeModelRepository models;

    public void validate(UUID orgId, BikeRequest request) {
        validate(orgId, request, null);
    }

    /**
     * Update variant: a customer that is unchanged is not re-checked, so a vehicle whose
     * owner was deleted can still be edited.
     */
    public void validate(UUID orgId, BikeRequest request, UUID currentCustomerId) {
        if ((currentCustomerId == null || !currentCustomerId.equals(request.customerId()))
                && !customers.existsByIdAndOrgIdAndDeletedFalse(request.customerId(), orgId)) {
            throw new NotFoundException("customer", request.customerId());
        }
        if (request.brandId() != null && !brands.existsByIdAndOrgId(request.brandId(), orgId)) {
            throw new NotFoundException("brand", request.brandId());
        }
        if (request.modelId() != null && !models.existsByIdAndOrgId(request.modelId(), orgId)) {
            throw new NotFoundException("model", request.modelId());
        }
    }
}
