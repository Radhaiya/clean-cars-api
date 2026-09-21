package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.CarModel;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the car-model CRUD. A model still referenced by a car hits the
 * DB foreign key and surfaces as 409 via {@code ApiExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class CarModelDeleteService {

    private final CarModelRepository models;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        CarModel model = models.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("model", id));
        models.delete(model);
    }
}
