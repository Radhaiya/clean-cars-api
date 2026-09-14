package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceCatalogRequest;
import com.example.cleancarsapi.dto.ServiceCatalogResponse;
import com.example.cleancarsapi.entity.ServiceCatalog;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UPDATE half of the service-catalog CRUD. */
@Service
@RequiredArgsConstructor
public class ServiceCatalogUpdateService {

    private final ServiceCatalogRepository catalog;
    private final ServiceCategoryLookup categoryLookup;

    @Transactional
    public ServiceCatalogResponse update(long orgId, long id, ServiceCatalogRequest request) {
        ServiceCatalog entry = catalog.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service", id));

        String name = request.name().trim();
        if (!entry.getName().equals(name) && catalog.existsByOrgIdAndNameAndIdNot(orgId, name, id)) {
            throw ConflictException.serviceCatalogNameExists(name);
        }
        String categoryName = categoryLookup.requireNameInOrg(orgId, request.categoryId());

        request.applyTo(entry);
        return ServiceCatalogResponse.from(catalog.save(entry), categoryName);
    }
}
