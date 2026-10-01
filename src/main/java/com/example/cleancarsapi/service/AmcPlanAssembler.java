package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.entity.AmcPlanItem;
import com.example.cleancarsapi.entity.AmcPlanVariant;
import com.example.cleancarsapi.entity.AmcVariantRow;
import com.example.cleancarsapi.repository.AmcPlanItemRepository;
import com.example.cleancarsapi.repository.AmcPlanVariantRepository;
import com.example.cleancarsapi.repository.AmcVariantRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Loads plans together with their service names, variants and price rows (batched) and builds {@link AmcPlanResponse}s. */
@Component
@RequiredArgsConstructor
public class AmcPlanAssembler {

    private final AmcPlanItemRepository items;
    private final AmcPlanVariantRepository variants;
    private final AmcVariantRowRepository rows;

    public AmcPlanResponse toResponse(AmcPlan plan) {
        return toResponses(List.of(plan)).get(0);
    }

    public List<AmcPlanResponse> toResponses(List<AmcPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<UUID> planIds = plans.stream().map(AmcPlan::getId).toList();
        Map<UUID, List<AmcPlanItem>> itemsByPlan = items.findByPlanIdInOrderByPositionAsc(planIds).stream()
                .collect(Collectors.groupingBy(AmcPlanItem::getPlanId));
        List<AmcPlanVariant> allVariants = variants.findByPlanIdInOrderByTenureMonthsAscIntervalMonthsAsc(planIds);
        Map<UUID, List<AmcPlanVariant>> variantsByPlan = allVariants.stream()
                .collect(Collectors.groupingBy(AmcPlanVariant::getPlanId));
        Map<UUID, List<AmcVariantRow>> rowsByVariant = allVariants.isEmpty() ? Map.of()
                : rows.findByVariantIdIn(allVariants.stream().map(AmcPlanVariant::getId).toList()).stream()
                        .collect(Collectors.groupingBy(AmcVariantRow::getVariantId));
        return plans.stream()
                .map(p -> AmcPlanResponse.from(p, itemsByPlan.getOrDefault(p.getId(), List.of()),
                        variantsByPlan.getOrDefault(p.getId(), List.of()), rowsByVariant))
                .toList();
    }
}
