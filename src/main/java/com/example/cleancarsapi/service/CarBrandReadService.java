package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarBrandResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarBrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** READ half of the car-brand CRUD — single fetch and paged listing. */
@Service
@RequiredArgsConstructor
public class CarBrandReadService {

    private final CarBrandRepository brands;

    @Transactional(readOnly = true)
    public CarBrandResponse get(UUID orgId, UUID id) {
        return brands.findByIdAndOrgId(id, orgId)
                .map(CarBrandResponse::from)
                .orElseThrow(() -> new NotFoundException("brand", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CarBrandResponse> list(UUID orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(brands.search(orgId, term, pageable).map(CarBrandResponse::from));
    }
}
