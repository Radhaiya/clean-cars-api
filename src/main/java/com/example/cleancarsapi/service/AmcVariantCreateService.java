package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.dto.AmcVariantRequest;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.entity.AmcPlanVariant;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcPlanItemRepository;
import com.example.cleancarsapi.repository.AmcPlanRepository;
import com.example.cleancarsapi.repository.AmcPlanVariantRepository;
import com.example.cleancarsapi.repository.AmcVariantRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** CREATE half of the AMC-variant CRUD. Returns the whole plan so the UI refreshes in one call. */
@Service
@RequiredArgsConstructor
public class AmcVariantCreateService {

    private final AmcPlanRepository plans;
    private final AmcPlanItemRepository items;
    private final AmcPlanVariantRepository variants;
    private final AmcVariantRowRepository rows;
    private final AmcVariantRowFactory rowFactory;
    private final AmcPlanGuard guard;
    private final AmcPlanAssembler assembler;

    @Transactional
    public AmcPlanResponse create(UUID orgId, UUID planId, AmcVariantRequest request) {
        guard.assertCanEdit(orgId);
        AmcPlan plan = plans.findByIdAndOrgId(planId, orgId).orElseThrow(() -> new NotFoundException("amc plan", planId));
        if (plan.isArchived()) {
            throw ConflictException.amcPlanArchived();
        }
        rowFactory.requireValidShape(request);
        if (variants.existsByPlanIdAndTenureMonthsAndIntervalMonths(planId, request.tenureMonths(), request.intervalMonths())) {
            throw ConflictException.amcVariantExists();
        }
        var planItems = items.findByPlanIdOrderByPositionAsc(planId);
        // Validate the rows before anything is written.
        AmcPlanVariant variant = new AmcPlanVariant();
        variant.setPlanId(planId);
        variant.setOrgId(orgId);
        variant.setTenureMonths(request.tenureMonths());
        variant.setIntervalMonths(request.intervalMonths());
        AmcPlanVariant saved = variants.save(variant);
        rows.saveAll(rowFactory.build(saved.getId(), planItems, request.rows()));
        return assembler.toResponse(plan);
    }
}
