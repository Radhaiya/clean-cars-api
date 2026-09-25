package com.example.cleancarsapi.dto.internal;

import java.util.UUID;

/**
 * The click-through response: a user's org in the full detail shape, or
 * {@code org: null} (not 404) for an org-less user — the console renders
 * "no org yet".
 */
public record InternalUserOrgResponse(UUID userId, InternalOrgDetailResponse org) {
}
