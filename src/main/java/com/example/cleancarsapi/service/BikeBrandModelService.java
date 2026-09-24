package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeBrandModels;
import com.example.cleancarsapi.entity.BikeModel;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import com.example.cleancarsapi.repository.BikeModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
/**
 * Read-only lookup: the org's bike brands each with their models, for populating the
 * "create bike" form. Not a CRUD resource, so it stays a single service.
 */
@Service
@RequiredArgsConstructor
public class BikeBrandModelService {

    private final BikeBrandRepository brands;
    private final BikeModelRepository models;

    @Transactional(readOnly = true)
    public List<BikeBrandModels> listByBrand(UUID orgId) {
        Map<UUID, List<BikeBrandModels.Model>> modelsByBrand = models.findByOrgIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.groupingBy(BikeModel::getBrandId,
                        Collectors.mapping(BikeBrandModels.Model::from, Collectors.toList())));

        return brands.findByOrgIdOrderByNameAsc(orgId).stream()
                .map(brand -> new BikeBrandModels(
                        brand.getId(),
                        brand.getName(),
                        modelsByBrand.getOrDefault(brand.getId(), List.of())))
                .toList();
    }
}
