package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarModelResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** READ half of the car-model CRUD — single fetch and paged listing (optionally by brand). */
@Service
@RequiredArgsConstructor
public class CarModelReadService {

    private final CarModelRepository models;

    @Transactional(readOnly = true)
    public CarModelResponse get(UUID orgId, UUID id) {
        return models.findByIdAndOrgId(id, orgId)
                .map(CarModelResponse::from)
                .orElseThrow(() -> new NotFoundException("model", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CarModelResponse> list(UUID orgId, UUID brandId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(models.search(orgId, brandId, term, pageable).map(CarModelResponse::from));
    }
}
