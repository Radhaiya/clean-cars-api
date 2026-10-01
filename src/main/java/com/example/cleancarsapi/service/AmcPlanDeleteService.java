package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.entity.AmcPlanVariant;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcPlanItemRepository;
import com.example.cleancarsapi.repository.AmcPlanRepository;
import com.example.cleancarsapi.repository.AmcPlanVariantRepository;
import com.example.cleancarsapi.repository.AmcSubscriptionRepository;
import com.example.cleancarsapi.repository.AmcVariantRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** DELETE half of the AMC-plan CRUD — only plans / variants that were never sold; sold ones can only be archived. */
@Service
@RequiredArgsConstructor
public class AmcPlanDeleteService {

    private final AmcPlanRepository plans;
    private final AmcPlanItemRepository items;
    private final AmcPlanVariantRepository variants;
    private final AmcVariantRowRepository rows;
    private final AmcSubscriptionRepository subscriptions;
    private final AmcPlanGuard guard;
    private final AmcPlanAssembler assembler;

    @Transactional
    public void deletePlan(UUID orgId, UUID id) {
        guard.assertCanEdit(orgId);
        AmcPlan plan = plans.findByIdAndOrgId(id, orgId).orElseThrow(() -> new NotFoundException("amc plan", id));
        if (subscriptions.existsByPlanId(id)) {
            throw ConflictException.amcPlanSold();
        }
        for (AmcPlanVariant v : variants.findByPlanId(id)) {
            rows.deleteByVariantId(v.getId());
        }
        rows.flush();
        variants.deleteAll(variants.findByPlanId(id));
        variants.flush();
        items.deleteAll(items.findByPlanIdOrderByPositionAsc(id));
        items.flush();
        plans.delete(plan);
    }

    /** Deletes one never-sold variant; returns the plan as it is afterwards. */
    @Transactional
    public AmcPlanResponse deleteVariant(UUID orgId, UUID planId, UUID variantId) {
        guard.assertCanEdit(orgId);
        AmcPlan plan = plans.findByIdAndOrgId(planId, orgId).orElseThrow(() -> new NotFoundException("amc plan", planId));
        AmcPlanVariant variant = variants.findByIdAndPlanIdAndOrgId(variantId, planId, orgId)
                .orElseThrow(() -> new NotFoundException("amc variant", variantId));
        if (subscriptions.existsByVariantId(variantId)) {
            throw ConflictException.amcVariantSold();
        }
        rows.deleteByVariantId(variantId);
        rows.flush();
        variants.delete(variant);
        variants.flush();
        return assembler.toResponse(plan);
    }
}
