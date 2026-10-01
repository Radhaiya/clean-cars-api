package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** READ half of the AMC-plan CRUD — single fetch and paged listing, each plan with its variants. */
@Service
@RequiredArgsConstructor
public class AmcPlanReadService {

    private final AmcPlanRepository plans;
    private final AmcPlanGuard guard;
    private final AmcPlanAssembler assembler;

    @Transactional(readOnly = true)
    public AmcPlanResponse get(UUID orgId, UUID id) {
        guard.assertCanRead(orgId);
        AmcPlan plan = plans.findByIdAndOrgId(id, orgId).orElseThrow(() -> new NotFoundException("amc plan", id));
        return assembler.toResponse(plan);
    }

    @Transactional(readOnly = true)
    public PageResponse<AmcPlanResponse> list(UUID orgId, String search, boolean includeArchived, Pageable pageable) {
        guard.assertCanRead(orgId);
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<AmcPlan> page = plans.search(orgId, term, includeArchived, pageable);
        List<AmcPlanResponse> content = assembler.toResponses(page.getContent());
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }
}
