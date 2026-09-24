package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeModelResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** READ half of the bike-model CRUD — single fetch and paged listing (optionally by brand). */
@Service
@RequiredArgsConstructor
public class BikeModelReadService {

    private final BikeModelRepository models;

    @Transactional(readOnly = true)
    public BikeModelResponse get(UUID orgId, UUID id) {
        return models.findByIdAndOrgId(id, orgId)
                .map(BikeModelResponse::from)
                .orElseThrow(() -> new NotFoundException("model", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BikeModelResponse> list(UUID orgId, UUID brandId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(models.search(orgId, brandId, term, pageable).map(BikeModelResponse::from));
    }
}
