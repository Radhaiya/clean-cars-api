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

/**
 * UPDATE half of the AMC-variant CRUD — edit tenure / frequency / rows, or archive / restore.
 * Sold AMCs hold their own snapshot, so editing a variant never changes what was already sold.
 */
@Service
@RequiredArgsConstructor
public class AmcVariantUpdateService {

    private final AmcPlanRepository plans;
    private final AmcPlanItemRepository items;
    private final AmcPlanVariantRepository variants;
    private final AmcVariantRowRepository rows;
    private final AmcVariantRowFactory rowFactory;
    private final AmcPlanGuard guard;
    private final AmcPlanAssembler assembler;

    @Transactional
    public AmcPlanResponse update(UUID orgId, UUID planId, UUID variantId, AmcVariantRequest request) {
        guard.assertCanEdit(orgId);
        AmcPlan plan = findPlan(orgId, planId);
        AmcPlanVariant variant = findVariant(orgId, planId, variantId);
        rowFactory.requireValidShape(request);
        if (variants.existsByPlanIdAndTenureMonthsAndIntervalMonthsAndIdNot(
                planId, request.tenureMonths(), request.intervalMonths(), variantId)) {
            throw ConflictException.amcVariantExists();
        }
        var newRows = rowFactory.build(variantId, items.findByPlanIdOrderByPositionAsc(planId), request.rows());

        variant.setTenureMonths(request.tenureMonths());
        variant.setIntervalMonths(request.intervalMonths());
        variants.save(variant);
        rows.deleteByVariantId(variantId);
        rows.flush();
        rows.saveAll(newRows);
        return assembler.toResponse(plan);
    }

    @Transactional
    public AmcPlanResponse setArchived(UUID orgId, UUID planId, UUID variantId, boolean archived) {
        guard.assertCanEdit(orgId);
        AmcPlan plan = findPlan(orgId, planId);
        AmcPlanVariant variant = findVariant(orgId, planId, variantId);
        variant.setArchived(archived);
        variants.save(variant);
        return assembler.toResponse(plan);
    }

    private AmcPlan findPlan(UUID orgId, UUID planId) {
        return plans.findByIdAndOrgId(planId, orgId).orElseThrow(() -> new NotFoundException("amc plan", planId));
    }

    private AmcPlanVariant findVariant(UUID orgId, UUID planId, UUID variantId) {
        return variants.findByIdAndPlanIdAndOrgId(variantId, planId, orgId)
                .orElseThrow(() -> new NotFoundException("amc variant", variantId));
    }
}
