package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceCatalogResponse;
import com.example.cleancarsapi.entity.ServiceCatalog;
import com.example.cleancarsapi.entity.ServiceCategory;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ServiceCatalogRepository;
import com.example.cleancarsapi.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** READ half of the service-catalog CRUD — single fetch and paged listing, with category names resolved. */
@Service
@RequiredArgsConstructor
public class ServiceCatalogReadService {

    private final ServiceCatalogRepository catalog;
    private final ServiceCategoryRepository categories;

    @Transactional(readOnly = true)
    public ServiceCatalogResponse get(long orgId, long id) {
        ServiceCatalog entry = catalog.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("service", id));
        String categoryName = entry.getCategoryId() == null ? null
                : categories.findByIdAndOrgId(entry.getCategoryId(), orgId)
                        .map(ServiceCategory::getName).orElse(null);
        return ServiceCatalogResponse.from(entry, categoryName);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceCatalogResponse> list(long orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<ServiceCatalog> page = catalog.search(orgId, term, pageable);

        List<Long> categoryIds = page.getContent().stream()
                .map(ServiceCatalog::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = categoryIds.isEmpty() ? Map.of()
                : categories.findByOrgIdAndIdIn(orgId, categoryIds).stream()
                        .collect(Collectors.toMap(ServiceCategory::getId, ServiceCategory::getName));

        return PageResponse.of(page.map(e -> {
            String categoryName = e.getCategoryId() == null ? null : names.get(e.getCategoryId());
            return ServiceCatalogResponse.from(e, categoryName);
        }));
    }
}
