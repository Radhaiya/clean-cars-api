package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarRequest;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarBrandRepository;
import com.example.cleancarsapi.repository.CarModelRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared check that a car's customer / brand / model all belong to the caller's org. */
@Component
@RequiredArgsConstructor
public class CarReferenceValidator {

    private final CustomerRepository customers;
    private final CarBrandRepository brands;
    private final CarModelRepository models;

    public void validate(long orgId, CarRequest request) {
        if (!customers.existsByIdAndOrgId(request.customerId(), orgId)) {
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
