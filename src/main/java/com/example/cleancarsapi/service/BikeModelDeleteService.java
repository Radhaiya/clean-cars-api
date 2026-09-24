package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.BikeModel;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the bike-model CRUD. A model still referenced by a bike hits the
 * DB foreign key and surfaces as 409 via {@code ApiExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class BikeModelDeleteService {

    private final BikeModelRepository models;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        BikeModel model = models.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("model", id));
        models.delete(model);
    }
}
