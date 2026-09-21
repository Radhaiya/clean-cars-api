package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarModelRequest;
import com.example.cleancarsapi.dto.CarModelResponse;
import com.example.cleancarsapi.entity.CarModel;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarBrandRepository;
import com.example.cleancarsapi.repository.CarModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** UPDATE half of the car-model CRUD. */
@Service
@RequiredArgsConstructor
public class CarModelUpdateService {

    private final CarModelRepository models;
    private final CarBrandRepository brands;

    @Transactional
    public CarModelResponse update(UUID orgId, UUID id, CarModelRequest request) {
        CarModel model = models.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("model", id));

        if (!brands.existsByIdAndOrgId(request.brandId(), orgId)) {
            throw new NotFoundException("brand", request.brandId());
        }

        String name = request.name().trim();
        boolean unchanged = model.getBrandId().equals(request.brandId()) && model.getName().equals(name);
        if (!unchanged
                && models.existsByOrgIdAndBrandIdAndNameAndIdNot(orgId, request.brandId(), name, id)) {
            throw ConflictException.carModelNameExists(name);
        }

        request.applyTo(model);
        return CarModelResponse.from(models.save(model));
    }
}
