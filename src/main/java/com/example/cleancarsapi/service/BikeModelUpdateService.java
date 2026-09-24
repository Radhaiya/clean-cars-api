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
/** UPDATE half of the bike-model CRUD. */
@Service
@RequiredArgsConstructor
public class BikeModelUpdateService {

    private final BikeModelRepository models;
    private final BikeBrandRepository brands;

    @Transactional
    public BikeModelResponse update(UUID orgId, UUID id, BikeModelRequest request) {
        BikeModel model = models.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("model", id));

        if (!brands.existsByIdAndOrgId(request.brandId(), orgId)) {
            throw new NotFoundException("brand", request.brandId());
        }

        String name = request.name().trim();
        boolean unchanged = model.getBrandId().equals(request.brandId()) && model.getName().equals(name);
        if (!unchanged
                && models.existsByOrgIdAndBrandIdAndNameAndIdNot(orgId, request.brandId(), name, id)) {
            throw ConflictException.bikeModelNameExists(name);
        }

        request.applyTo(model);
        return BikeModelResponse.from(models.save(model));
    }
}
