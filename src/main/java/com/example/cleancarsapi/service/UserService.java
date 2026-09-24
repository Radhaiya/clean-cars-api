package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.UserProfile;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.UserRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final CarRepository cars;
    private final BikeRepository bikes;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("user", userId));

        if (user.getOrgId() == null) {
            return UserProfile.of(user, null, null, null);
        }
        UUID orgId = user.getOrgId();

        Organization org = organizations.findById(orgId).orElse(null);
        String orgName = org != null ? org.getName() : null;
        String orgTimezone = org != null ? org.getTimezone() : null;

        UserProfile.PlanUsage plan = planUsage(orgId);

        return UserProfile.of(user, orgName, orgTimezone, plan);
    }

    /**
     * Voluntary exit from the org ({@code POST /api/org/leave}). The owner cannot
     * leave (the org would be owner-less — leaving for org-less members instead of
     * starting a different flow); any invited member can. After leaving, the user
     * is org-less again: they may accept an invite or start a trial.
     */
    @Transactional
    public void leaveOrg() {
        User user = users.findByIdForUpdate(AuthContext.require().userId())
                .orElseThrow(() -> new NotFoundException("user", AuthContext.require().userId()));
        if (user.getOrgId() == null) {
            throw ConflictException.userNotInOrg();
        }
        if (user.getRole() == UserRole.OWNER) {
            throw ConflictException.ownerCannotLeave();
        }
        user.leaveOrg();
    }

    private UserProfile.PlanUsage planUsage(UUID orgId) {
        CurrentSubscriptionResponse subscription = subscriptionService.getCurrentForOrg(orgId);
        if (subscription.plan() == null) {
            // Never subscribed (or org-less): PlanResponse blocks are only present on a real row.
            return null;
        }
        var limits = subscription.plan().limits();
        var features = subscription.plan().features();
        return new UserProfile.PlanUsage(
                subscription.plan().name(),
                subscription.plan().isTrial(),
                subscription.billingCycle(),
                SubscriptionStatus.valueOf(subscription.status()),
                limits.maxUsers(),
                users.countByOrgId(orgId),
                limits.maxCars(),
                cars.countByOrgId(orgId),
                bikes.countByOrgId(orgId),
                features.reportWindowMonths(),
                features.statsRangeYears());
    }
}
