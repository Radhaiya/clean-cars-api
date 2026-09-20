package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.UserProfile;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final CarRepository cars;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public UserProfile getProfile(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("user", userId));

        if (user.getOrgId() == null) {
            return UserProfile.of(user, null, null, null);
        }
        long orgId = user.getOrgId();

        Organization org = organizations.findById(orgId).orElse(null);
        String orgName = org != null ? org.getName() : null;
        String orgTimezone = org != null ? org.getTimezone() : null;

        UserProfile.PlanUsage plan = planUsage(orgId);

        return UserProfile.of(user, orgName, orgTimezone, plan);
    }

    private UserProfile.PlanUsage planUsage(long orgId) {
        CurrentSubscriptionResponse subscription = subscriptionService.getCurrentForOrg(orgId);
        if (!subscription.active()) {
            return null;
        }
        var limits = subscription.plan().limits();
        var features = subscription.plan().features();
        return new UserProfile.PlanUsage(
                subscription.plan().name(),
                subscription.plan().isTrial(),
                limits.maxUsers(),
                users.countByOrgId(orgId),
                limits.maxCars(),
                cars.countByOrgId(orgId),
                features.reportWindowMonths(),
                features.statsRangeYears());
    }
}
