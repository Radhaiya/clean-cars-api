package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;

/**
 * Safe view of the authenticated user — never exposes the password hash.
 * {@code plan} is present only when the org has a live subscription (trial or
 * paid) — {@code null} for an org-less caller or one whose trial/plan has ended.
 */
public record UserProfile(
        Long id,
        String name,
        String email,
        String phone,
        UserRole role,
        String status,
        Long orgId,
        String orgName,
        PlanUsage plan
) {
    /**
     * {@code maxUsers} / {@code maxCars} / {@code reportWindowMonths} / {@code statsRangeYears}
     * null = unlimited (same convention as {@code PlanResponse.Limits}/{@code Features});
     * {@code statsRangeYears} 0 = statistics page hidden entirely.
     */
    public record PlanUsage(
            String planName,
            boolean isTrial,
            Integer maxUsers,
            long currentUsers,
            Integer maxCars,
            long currentCars,
            Integer reportWindowMonths,
            Integer statsRangeYears
    ) {
    }

    public static UserProfile of(User user, String orgName, PlanUsage plan) {
        return new UserProfile(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getOrgId(),
                orgName,
                plan);
    }
}
