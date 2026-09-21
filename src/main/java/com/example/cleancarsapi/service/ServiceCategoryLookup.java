package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.ServiceCategory;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Shared: validate a service-catalog entry's optional {@code categoryId} against the caller's org. */
@Component
@RequiredArgsConstructor
public class ServiceCategoryLookup {

    private final ServiceCategoryRepository categories;

    /**
     * Null {@code categoryId} → null. Otherwise the category must belong to {@code orgId}
     * (404 if not) and its name is returned for the response.
     */
    public String requireNameInOrg(UUID orgId, UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findByIdAndOrgId(categoryId, orgId)
                .map(ServiceCategory::getName)
                .orElseThrow(() -> new NotFoundException("category", categoryId));
    }
}
