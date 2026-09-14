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

/** CREATE half of the car-model CRUD. */
@Service
@RequiredArgsConstructor
public class CarModelCreateService {

    private final CarModelRepository models;
    private final CarBrandRepository brands;

    @Transactional
    public CarModelResponse create(long orgId, CarModelRequest request) {
        if (!brands.existsByIdAndOrgId(request.brandId(), orgId)) {
            throw new NotFoundException("brand", request.brandId());
        }

        String name = request.name().trim();
        if (models.existsByOrgIdAndBrandIdAndName(orgId, request.brandId(), name)) {
            throw ConflictException.carModelNameExists(name);
        }

        CarModel model = new CarModel();
        model.setOrgId(orgId);
        request.applyTo(model);
        return CarModelResponse.from(models.save(model));
    }
}
