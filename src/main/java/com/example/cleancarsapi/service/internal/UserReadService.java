package com.example.cleancarsapi.service.internal;

import com.example.cleancarsapi.dto.internal.InternalOrgDetailResponse;
import com.example.cleancarsapi.dto.internal.InternalUserOrgResponse;
import com.example.cleancarsapi.dto.internal.InternalUserSummaryResponse;
import com.example.cleancarsapi.dto.internal.ConsoleTimes;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.Subscription;
import com.example.cleancarsapi.entity.SubscriptionStatus;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only user views for the internal console (identity/account users, not
 * console whitelists). The click-through ({@code /users/{id}/org}) is the org
 * detail view, resolved via {@code users.org_id → organizations} — {@code org:
 * null} (not 404) for an org-less user.
 */
@Service
@RequiredArgsConstructor
public class UserReadService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final SubscriptionReadService subscriptions;
    private final OrgReadService orgReadService;

    @Transactional(readOnly = true)
    public Page<InternalUserSummaryResponse> list(String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search;
        Page<User> page = users.searchAcrossOrgs(term, pageable);

        // Resolve each page's distinct org once (org zones + names for the response).
        Map<UUID, Organization> orgById = new HashMap<>();
        page.getContent().stream()
                .filter(u -> u.getOrgId() != null)
                .map(User::getOrgId)
                .distinct()
                .forEach(orgId -> organizations.findById(orgId)
                        .ifPresent(org -> orgById.put(orgId, org)));

        return page.map(user -> toSummary(user, orgById.get(user.getOrgId())));
    }

    /** The click-through: the user's org in the full detail shape; {@code org: null} when org-less. */
    @Transactional(readOnly = true)
    public InternalUserOrgResponse findOrg(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("user", userId));
        InternalOrgDetailResponse org = user.getOrgId() == null
                ? null
                : organizations.findById(user.getOrgId()).map(orgReadService::toDetail).orElse(null);
        return new InternalUserOrgResponse(userId, org);
    }

    private InternalUserSummaryResponse toSummary(User user, Organization org) {
        String timezone = org == null ? null : org.getTimezone();

        // Plan state: same live-lifecycle read as the org views — the most recent
        // live-status subscription row only; a terminal/absent row means NONE.
        String planName = null;
        String planStatus = null;
        LocalDate planExpiry = null;
        Long trialDaysRemaining = null;
        if (user.getOrgId() != null) {
            var live = subscriptions.liveForOrg(user.getOrgId()).orElse(null);
            if (live != null) {
                Subscription sub = live.subscription();
                planName = live.plan().getName();
                planStatus = sub.getStatus().name();
                planExpiry = sub.getEndDate();
                if (sub.getStatus() == SubscriptionStatus.TRIALING && sub.getEndDate() != null) {
                    trialDaysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), sub.getEndDate()));
                }
            }
        }

        return new InternalUserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getOrgId(),
                org == null ? null : org.getName(),
                user.getRole().name(),
                user.isTrialUsed(),
                user.getStatus(),
                ConsoleTimes.inZone(user.getCreatedAt(), timezone),
                planName,
                planStatus,
                planExpiry,
                trialDaysRemaining);
    }
}
