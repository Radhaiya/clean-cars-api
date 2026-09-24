package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeBrandResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeBrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** READ half of the bike-brand CRUD — single fetch and paged listing. */
@Service
@RequiredArgsConstructor
public class BikeBrandReadService {

    private final BikeBrandRepository brands;

    @Transactional(readOnly = true)
    public BikeBrandResponse get(UUID orgId, UUID id) {
        return brands.findByIdAndOrgId(id, orgId)
                .map(BikeBrandResponse::from)
                .orElseThrow(() -> new NotFoundException("brand", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BikeBrandResponse> list(UUID orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(brands.search(orgId, term, pageable).map(BikeBrandResponse::from));
    }
}
