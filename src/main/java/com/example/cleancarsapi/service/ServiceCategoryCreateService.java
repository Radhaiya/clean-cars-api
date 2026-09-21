package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceCategoryRequest;
import com.example.cleancarsapi.dto.ServiceCategoryResponse;
import com.example.cleancarsapi.entity.ServiceCategory;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the service-category CRUD. */
@Service
@RequiredArgsConstructor
public class ServiceCategoryCreateService {

    private final ServiceCategoryRepository categories;

    @Transactional
    public ServiceCategoryResponse create(UUID orgId, ServiceCategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByOrgIdAndName(orgId, name)) {
            throw ConflictException.serviceCategoryNameExists(name);
        }

        ServiceCategory category = new ServiceCategory();
        category.setOrgId(orgId);
        request.applyTo(category);
        return ServiceCategoryResponse.from(categories.save(category));
    }
}
