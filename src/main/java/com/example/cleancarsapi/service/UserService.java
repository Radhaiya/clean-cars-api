package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CurrentSubscriptionResponse;
import com.example.cleancarsapi.dto.UserProfile;
import com.example.cleancarsapi.dto.UserUpdateRequest;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
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
    private final EmployeeRepository employees;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("user", userId));

        if (user.getOrgId() == null) {
            return UserProfile.of(user, null, null, null, null, null, null, null);
        }
        UUID orgId = user.getOrgId();

        Organization org = organizations.findById(orgId).orElse(null);
        String orgName = org != null ? org.getName() : null;
        String orgTimezone = org != null ? org.getTimezone() : null;
        String orgTaxName = org != null ? org.getTaxName() : null;

        String phoneIso = org != null ? org.getPhoneCountryIso() : null;
        String phoneDial = org != null ? org.getPhoneDialCode() : null;
        // Only an org that hasn't chosen yet gets a guess: the caller's OTP-verified phone
        // is the strongest signal, the org currency a weaker one (shared currencies give none).
        String suggested = org == null || phoneIso != null ? null
                : CountryDialCodes.isoForPhone(user.getPhone())
                        .or(() -> CountryDialCodes.isoForCurrency(org.getCurrencyCode()))
                        .orElse(null);

        UserProfile.PlanUsage plan = planUsage(orgId);

        return UserProfile.of(user, orgName, orgTimezone, orgTaxName, phoneIso, phoneDial, suggested, plan);
    }

    /** Self-service profile edit ({@code PUT /api/me}) — name and phone only; see {@link UserUpdateRequest}. */
    @Transactional
    public UserProfile updateProfile(UUID userId, UserUpdateRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("user", userId));
        String dialCode = user.getOrgId() == null ? null
                : organizations.findById(user.getOrgId()).map(Organization::getPhoneDialCode).orElse(null);
        user.updateProfile(request.name().trim(), normalizePhone(request.phone(), dialCode));
        users.save(user);
        return getProfile(userId);
    }

    /** E.164-ish: digits with a leading {@code +}; a local number gets the org's dial code (trunk 0 dropped). */
    private static String normalizePhone(String raw, String dialCode) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("[\\s\\-().]", "");
        if (compact.startsWith("00")) {
            compact = "+" + compact.substring(2);
        }
        if (!compact.matches("\\+?\\d{4,15}")) {
            throw new com.example.cleancarsapi.exception.BadRequestException("Enter a valid phone number");
        }
        if (compact.startsWith("+")) {
            return compact;
        }
        if (dialCode == null) {
            throw com.example.cleancarsapi.exception.ConflictException.phoneNeedsCountryCode();
        }
        return dialCode + compact.replaceFirst("^0+", "");
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
                employees.countByOrgId(orgId),
                limits.maxCars(),
                cars.countByOrgIdAndDeletedFalse(orgId),
                bikes.countByOrgIdAndDeletedFalse(orgId),
                features.reportWindowMonths(),
                features.statsRangeYears(),
                features.amcEnabled());
    }
}
