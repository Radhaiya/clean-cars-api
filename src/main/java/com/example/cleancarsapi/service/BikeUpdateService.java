package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeRequest;
import com.example.cleancarsapi.dto.BikeResponse;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
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

    @Transactional
    public BikeResponse update(UUID orgId, UUID id, BikeRequest request) {
        Bike bike = bikes.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("bike", id));

        references.validate(orgId, request);
        request.applyTo(bike);
        return BikeResponse.from(bikes.save(bike));
    }
}
