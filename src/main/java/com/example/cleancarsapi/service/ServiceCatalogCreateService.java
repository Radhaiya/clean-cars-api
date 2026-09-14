package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceCatalogRequest;
import com.example.cleancarsapi.dto.ServiceCatalogResponse;
import com.example.cleancarsapi.entity.ServiceCatalog;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.ServiceCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CREATE half of the service-catalog CRUD. */
@Service
@RequiredArgsConstructor
public class ServiceCatalogCreateService {

    private final ServiceCatalogRepository catalog;
    private final ServiceCategoryLookup categoryLookup;

    @Transactional
    public ServiceCatalogResponse create(long orgId, ServiceCatalogRequest request) {
        String name = request.name().trim();
        if (catalog.existsByOrgIdAndName(orgId, name)) {
            throw ConflictException.serviceCatalogNameExists(name);
        }
        String categoryName = categoryLookup.requireNameInOrg(orgId, request.categoryId());

        ServiceCatalog entry = new ServiceCatalog();
        entry.setOrgId(orgId);
        request.applyTo(entry);
        return ServiceCatalogResponse.from(catalog.save(entry), categoryName);
    }
}
