package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeRequest;
import com.example.cleancarsapi.dto.BikeResponse;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.BikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the bike CRUD. */
@Service
@RequiredArgsConstructor
public class BikeCreateService {

    private final BikeRepository bikes;
    private final BikeReferenceValidator references;

    @Transactional
    public BikeResponse create(UUID orgId, BikeRequest request) {
        references.validate(orgId, request);
        if (bikes.existsByNumberKey(orgId, NormalizedKeys.vehicle(request.bikeNumber()), new java.util.UUID(0L, 0L))) {
            throw ConflictException.bikeNumberExists(request.bikeNumber().trim());
        }

        Bike bike = new Bike();
        bike.setOrgId(orgId);
        request.applyTo(bike);
        return BikeResponse.from(bikes.save(bike), false);
    }
}
