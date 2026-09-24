package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeModelRequest;
import com.example.cleancarsapi.dto.BikeModelResponse;
import com.example.cleancarsapi.entity.BikeModel;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import com.example.cleancarsapi.repository.BikeModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the bike-model CRUD. */
@Service
@RequiredArgsConstructor
public class BikeModelCreateService {

    private final BikeModelRepository models;
    private final BikeBrandRepository brands;

    @Transactional
    public BikeModelResponse create(UUID orgId, BikeModelRequest request) {
        if (!brands.existsByIdAndOrgId(request.brandId(), orgId)) {
            throw new NotFoundException("brand", request.brandId());
        }

        String name = request.name().trim();
        if (models.existsByOrgIdAndBrandIdAndName(orgId, request.brandId(), name)) {
            throw ConflictException.bikeModelNameExists(name);
        }

        BikeModel model = new BikeModel();
        model.setOrgId(orgId);
        request.applyTo(model);
        return BikeModelResponse.from(models.save(model));
    }
}
