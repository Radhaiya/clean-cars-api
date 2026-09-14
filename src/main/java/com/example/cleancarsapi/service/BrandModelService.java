package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BrandModels;
import com.example.cleancarsapi.entity.CarModel;
import com.example.cleancarsapi.repository.CarBrandRepository;
import com.example.cleancarsapi.repository.CarModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only lookup: the org's brands each with their models, for populating the
 * "create car" form. Not a CRUD resource, so it stays a single service.
 */
@Service
@RequiredArgsConstructor
public class BrandModelService {

    private final CarBrandRepository brands;
    private final CarModelRepository models;

    @Transactional(readOnly = true)
    public List<BrandModels> listByBrand(long orgId) {
        Map<Long, List<BrandModels.Model>> modelsByBrand = models.findByOrgIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.groupingBy(CarModel::getBrandId,
                        Collectors.mapping(BrandModels.Model::from, Collectors.toList())));

        return brands.findByOrgIdOrderByNameAsc(orgId).stream()
                .map(brand -> new BrandModels(
                        brand.getId(),
                        brand.getName(),
                        modelsByBrand.getOrDefault(brand.getId(), List.of())))
                .toList();
    }
}
