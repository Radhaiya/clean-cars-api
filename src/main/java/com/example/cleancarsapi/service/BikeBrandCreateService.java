package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeBrandRequest;
import com.example.cleancarsapi.dto.BikeBrandResponse;
import com.example.cleancarsapi.entity.BikeBrand;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the bike-brand CRUD. */
@Service
@RequiredArgsConstructor
public class BikeBrandCreateService {

    private final BikeBrandRepository brands;

    @Transactional
    public BikeBrandResponse create(UUID orgId, BikeBrandRequest request) {
        String name = request.name().trim();
        if (brands.existsByOrgIdAndName(orgId, name)) {
            throw ConflictException.bikeBrandNameExists(name);
        }

        BikeBrand brand = new BikeBrand();
        brand.setOrgId(orgId);
        request.applyTo(brand);
        return BikeBrandResponse.from(brands.save(brand));
    }
}
