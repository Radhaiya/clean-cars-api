package com.example.cleancarsapi.config;

import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The caller's org timezone, resolved from {@code organizations.timezone} once
 * per request and memoised — the single source for both where business
 * day/month boundaries are drawn (this class's {@link #now()}) and how
 * timestamps are rendered at the JSON boundary ({@link TimezoneJacksonConfig}
 * consumes the same memoised attribute). Unauthenticated or org-less callers
 * get UTC.
 */
@Component
@RequiredArgsConstructor
public class OrgTimeZoneResolver {

    private static final String ZONE_ATTRIBUTE = "org.timezone";

    private final OrganizationRepository organizations;

    /** The caller's org zone; UTC when there is no org on the call. Memoised per request. */
    public ZoneId zone() {
        AuthenticatedUser user = currentUser();
        if (user == null || !user.hasOrg()) {
            return ZoneOffset.UTC;
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            Object cached = request.getAttribute(ZONE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof ZoneId zone) {
                return zone;
            }
        }
        ZoneId zone = organizations.findById(user.requireOrgId())
                .map(Organization::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneOffset.UTC);
        if (request != null) {
            request.setAttribute(ZONE_ATTRIBUTE, zone, RequestAttributes.SCOPE_REQUEST);
        }
        return zone;
    }

    /** The current wall time in the caller's org zone — the clock all dashboard day/month math runs on. */
    public LocalDateTime now() {
        return LocalDateTime.now(zone());
    }

    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user;
    }
}
