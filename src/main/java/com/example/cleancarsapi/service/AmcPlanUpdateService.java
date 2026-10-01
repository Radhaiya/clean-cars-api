package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.AmcPlanRenameRequest;
import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.AmcPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** UPDATE half of the AMC-plan CRUD — rename and archive/restore. The service names are fixed. */
@Service
@RequiredArgsConstructor
public class AmcPlanUpdateService {

    private final AmcPlanRepository plans;
    private final AmcPlanGuard guard;
    private final AmcPlanAssembler assembler;

    @Transactional
    public AmcPlanResponse rename(UUID orgId, UUID id, AmcPlanRenameRequest request) {
        guard.assertCanEdit(orgId);
        AmcPlan plan = find(orgId, id);
        String name = request.name().trim();
        if (!plan.getName().equals(name) && plans.existsByOrgIdAndNameAndIdNot(orgId, name, id)) {
            throw ConflictException.amcPlanNameExists(name);
        }
        plan.setName(name);
        return assembler.toResponse(plans.save(plan));
    }

    @Transactional
    public AmcPlanResponse setArchived(UUID orgId, UUID id, boolean archived) {
        guard.assertCanEdit(orgId);
        AmcPlan plan = find(orgId, id);
        plan.setArchived(archived);
        return assembler.toResponse(plans.save(plan));
    }

    private AmcPlan find(UUID orgId, UUID id) {
        return plans.findByIdAndOrgId(id, orgId).orElseThrow(() -> new NotFoundException("amc plan", id));
    }
}
