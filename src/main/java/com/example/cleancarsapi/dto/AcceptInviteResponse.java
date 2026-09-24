package com.example.cleancarsapi.dto;

/**
 * Result of accepting an invite: the caller is now in the org, so a fresh access
 * token is returned carrying the new {@code org_id} and role (the caller's old
 * token has neither — org-less/worker tokens). Mirrors {@link StartTrialResponse}'s
 * token block; the existing refresh token stays valid.
 */
public record AcceptInviteResponse(
        String token,
        String tokenType,
        long expiresIn
) {
    public static AcceptInviteResponse of(String token, long expiresIn) {
        return new AcceptInviteResponse(token, "Bearer", expiresIn);
    }
}
