package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.VendorResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** READ half of the vendor CRUD — single fetch and paged listing. */
@Service
@RequiredArgsConstructor
public class VendorReadService {

    private final VendorRepository vendors;

    @Transactional(readOnly = true)
    public VendorResponse get(long orgId, long id) {
        return vendors.findByIdAndOrgId(id, orgId)
                .map(VendorResponse::from)
                .orElseThrow(() -> new NotFoundException("vendor", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<VendorResponse> list(long orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(vendors.search(orgId, term, pageable).map(VendorResponse::from));
    }
}
