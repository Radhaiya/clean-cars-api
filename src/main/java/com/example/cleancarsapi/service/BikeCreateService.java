package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeRequest;
import com.example.cleancarsapi.dto.BikeResponse;
import com.example.cleancarsapi.entity.Bike;
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

        Bike bike = new Bike();
        bike.setOrgId(orgId);
        request.applyTo(bike);
        return BikeResponse.from(bikes.save(bike));
    }
}
