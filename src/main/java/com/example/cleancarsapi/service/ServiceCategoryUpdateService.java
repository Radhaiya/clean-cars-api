package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceCategoryRequest;
import com.example.cleancarsapi.dto.ServiceCategoryResponse;
import com.example.cleancarsapi.entity.ServiceCategory;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UPDATE half of the service-category CRUD. */
@Service
@RequiredArgsConstructor
public class ServiceCategoryUpdateService {

    private final ServiceCategoryRepository categories;

    @Transactional
    public ServiceCategoryResponse update(long orgId, long id, ServiceCategoryRequest request) {
        ServiceCategory category = categories.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("category", id));

        String name = request.name().trim();
        if (!category.getName().equals(name) && categories.existsByOrgIdAndNameAndIdNot(orgId, name, id)) {
            throw ConflictException.serviceCategoryNameExists(name);
        }

        request.applyTo(category);
        return ServiceCategoryResponse.from(categories.save(category));
    }
}
