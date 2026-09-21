package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.ServiceCatalog;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the service-catalog CRUD. A hard delete — an entry still
 * referenced by a service order is protected by the DB FK, which
 * {@code ApiExceptionHandler} maps to 409.
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogDeleteService {

    private final ServiceCatalogRepository catalog;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        ServiceCatalog entry = catalog.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service", id));
        catalog.delete(entry);
    }
}
