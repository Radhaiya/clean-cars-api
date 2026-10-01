package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ForbiddenException;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The two checks every AMC-plan endpoint makes: the org's plan includes AMC, and (for writes) the caller may edit plans. */
@Component
@RequiredArgsConstructor
public class AmcPlanGuard {

    private final PlanLimitService planLimits;

    /** Anyone in the org may read plans, as long as the org's subscription plan includes AMC. */
    public void assertCanRead(UUID orgId) {
        planLimits.assertAmcEnabled(orgId);
    }

    /** Owner / manager (and legacy admin) may create and edit plans and variants. */
    public void assertCanEdit(UUID orgId) {
        planLimits.assertAmcEnabled(orgId);
        UserRole role = AuthContext.require().role();
        if (role != UserRole.OWNER && role != UserRole.MANAGER && role != UserRole.ADMIN) {
            throw new ForbiddenException("Only the owner or a manager can edit AMC plans");
        }
    }
}
