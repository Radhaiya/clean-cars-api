package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.ServiceCategory;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DELETE half of the service-category CRUD. Services keep their row — the
 * {@code service_catalog.category_id} FK is {@code ON DELETE SET NULL}, so any
 * services in this category simply become uncategorized.
 */
@Service
@RequiredArgsConstructor
public class ServiceCategoryDeleteService {

    private final ServiceCategoryRepository categories;

    @Transactional
    public void delete(long orgId, long id) {
        ServiceCategory category = categories.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("category", id));
        categories.delete(category);
    }
}
