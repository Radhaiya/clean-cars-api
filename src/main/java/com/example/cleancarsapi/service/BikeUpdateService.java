package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeRequest;
import com.example.cleancarsapi.dto.BikeResponse;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** UPDATE half of the bike CRUD. */
@Service
@RequiredArgsConstructor
public class BikeUpdateService {

    private final BikeRepository bikes;
    private final BikeReferenceValidator references;
    private final CustomerRepository customers;

    @Transactional
    public BikeResponse update(UUID orgId, UUID id, BikeRequest request) {
        Bike bike = bikes.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("bike", id));

        references.validate(orgId, request, bike.getCustomerId());
        if (bikes.existsByNumberKey(orgId, NormalizedKeys.vehicle(request.bikeNumber()), id)) {
            throw ConflictException.bikeNumberExists(request.bikeNumber().trim());
        }
        request.applyTo(bike);
        return BikeResponse.from(bikes.save(bike), isOwnerDeleted(orgId, bike.getCustomerId()));
    }

    /**
     * Hands the bike to another live customer — works for a live or a deleted current owner.
     * Past service orders keep the customer they were opened for; only new orders follow the new owner.
     */
    @Transactional
    public BikeResponse transferOwner(UUID orgId, UUID id, UUID newCustomerId) {
        Bike bike = bikes.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("bike", id));
        if (bike.getCustomerId().equals(newCustomerId)) {
            throw ConflictException.ownerUnchanged();
        }
        if (!customers.existsByIdAndOrgIdAndDeletedFalse(newCustomerId, orgId)) {
            throw new NotFoundException("customer", newCustomerId);
        }
        bike.setCustomerId(newCustomerId);
        return BikeResponse.from(bikes.save(bike), false);
    }

    private boolean isOwnerDeleted(UUID orgId, UUID customerId) {
        return customers.findByIdAndOrgId(customerId, orgId).map(Customer::isDeleted).orElse(false);
    }
}
