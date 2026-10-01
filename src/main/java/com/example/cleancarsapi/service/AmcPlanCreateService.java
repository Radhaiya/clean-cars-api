package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcPlanRequest;
import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.entity.AmcPlanItem;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.AmcPlanItemRepository;
import com.example.cleancarsapi.repository.AmcPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** CREATE half of the AMC-plan CRUD — a plan and its fixed service names. Variants are created separately. */
@Service
@RequiredArgsConstructor
public class AmcPlanCreateService {

    private final AmcPlanRepository plans;
    private final AmcPlanItemRepository items;
    private final AmcPlanGuard guard;
    private final AmcPlanAssembler assembler;

    @Transactional
    public AmcPlanResponse create(UUID orgId, AmcPlanRequest request) {
        guard.assertCanEdit(orgId);
        String name = request.name().trim();
        if (plans.existsByOrgIdAndName(orgId, name)) {
            throw ConflictException.amcPlanNameExists(name);
        }

        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : request.serviceNames()) {
            String serviceName = raw.trim();
            if (!seen.add(serviceName.toLowerCase())) {
                throw new BadRequestException("Duplicate service name in the plan: " + serviceName);
            }
            names.add(serviceName);
        }

        AmcPlan plan = new AmcPlan();
        plan.setOrgId(orgId);
        plan.setName(name);
        AmcPlan saved = plans.save(plan);

        List<AmcPlanItem> rows = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            AmcPlanItem item = new AmcPlanItem();
            item.setPlanId(saved.getId());
            item.setServiceName(names.get(i));
            item.setPosition(i);
            rows.add(item);
        }
        items.saveAll(rows);
        return assembler.toResponse(saved);
    }
}
