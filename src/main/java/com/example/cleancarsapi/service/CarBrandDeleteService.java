package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.CarBrand;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarBrandRepository;
import com.example.cleancarsapi.repository.CarModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** DELETE half of the car-brand CRUD. Refuses to delete a brand that still has models. */
@Service
@RequiredArgsConstructor
public class CarBrandDeleteService {

    private final CarBrandRepository brands;
    private final CarModelRepository models;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        CarBrand brand = brands.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("brand", id));

        if (models.existsByBrandId(brand.getId())) {
            throw ConflictException.carBrandInUse();
        }

        brands.delete(brand);
    }
}
