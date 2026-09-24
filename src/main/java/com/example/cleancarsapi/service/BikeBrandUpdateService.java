package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeBrandRequest;
import com.example.cleancarsapi.dto.BikeBrandResponse;
import com.example.cleancarsapi.entity.BikeBrand;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** UPDATE half of the bike-brand CRUD. */
@Service
@RequiredArgsConstructor
public class BikeBrandUpdateService {

    private final BikeBrandRepository brands;

    @Transactional
    public BikeBrandResponse update(UUID orgId, UUID id, BikeBrandRequest request) {
        BikeBrand brand = brands.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("brand", id));

        String name = request.name().trim();
        if (!brand.getName().equals(name) && brands.existsByOrgIdAndNameAndIdNot(orgId, name, id)) {
            throw ConflictException.bikeBrandNameExists(name);
        }

        request.applyTo(brand);
        return BikeBrandResponse.from(brands.save(brand));
    }
}
