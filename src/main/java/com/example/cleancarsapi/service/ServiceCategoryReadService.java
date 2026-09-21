package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceCategoryResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** READ half of the service-category CRUD — single fetch and paged listing. */
@Service
@RequiredArgsConstructor
public class ServiceCategoryReadService {

    private final ServiceCategoryRepository categories;

    @Transactional(readOnly = true)
    public ServiceCategoryResponse get(UUID orgId, UUID id) {
        return categories.findByIdAndOrgId(id, orgId)
                .map(ServiceCategoryResponse::from)
                .orElseThrow(() -> new NotFoundException("category", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceCategoryResponse> list(UUID orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(categories.search(orgId, term, pageable).map(ServiceCategoryResponse::from));
    }
}
