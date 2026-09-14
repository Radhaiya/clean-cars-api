package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarBrandRequest;
import com.example.cleancarsapi.dto.CarBrandResponse;
import com.example.cleancarsapi.entity.CarBrand;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarBrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UPDATE half of the car-brand CRUD. */
@Service
@RequiredArgsConstructor
public class CarBrandUpdateService {

    private final CarBrandRepository brands;

    @Transactional
    public CarBrandResponse update(long orgId, long id, CarBrandRequest request) {
        CarBrand brand = brands.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("brand", id));

        String name = request.name().trim();
        if (!brand.getName().equals(name) && brands.existsByOrgIdAndNameAndIdNot(orgId, name, id)) {
            throw ConflictException.carBrandNameExists(name);
        }

        request.applyTo(brand);
        return CarBrandResponse.from(brands.save(brand));
    }
}
