package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarBrandRequest;
import com.example.cleancarsapi.dto.CarBrandResponse;
import com.example.cleancarsapi.entity.CarBrand;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.CarBrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CREATE half of the car-brand CRUD. */
@Service
@RequiredArgsConstructor
public class CarBrandCreateService {

    private final CarBrandRepository brands;

    @Transactional
    public CarBrandResponse create(long orgId, CarBrandRequest request) {
        String name = request.name().trim();
        if (brands.existsByOrgIdAndName(orgId, name)) {
            throw ConflictException.carBrandNameExists(name);
        }

        CarBrand brand = new CarBrand();
        brand.setOrgId(orgId);
        request.applyTo(brand);
        return CarBrandResponse.from(brands.save(brand));
    }
}
