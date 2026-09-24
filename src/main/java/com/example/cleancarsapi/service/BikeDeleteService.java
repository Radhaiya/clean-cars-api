package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the bike CRUD. A bike still referenced by a service order hits the
 * DB foreign key and surfaces as 409 via {@code ApiExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class BikeDeleteService {

    private final BikeRepository bikes;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Bike bike = bikes.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("bike", id));
        bikes.delete(bike);
    }
}
