package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.BikeBrand;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import com.example.cleancarsapi.repository.BikeModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** DELETE half of the bike-brand CRUD. Refuses to delete a brand that still has models. */
@Service
@RequiredArgsConstructor
public class BikeBrandDeleteService {

    private final BikeBrandRepository brands;
    private final BikeModelRepository models;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        BikeBrand brand = brands.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("brand", id));

        if (models.existsByBrandId(brand.getId())) {
            throw ConflictException.bikeBrandInUse();
        }

        brands.delete(brand);
    }
}
