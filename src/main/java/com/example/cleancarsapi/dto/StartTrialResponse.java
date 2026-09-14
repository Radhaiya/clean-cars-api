package com.example.cleancarsapi.dto;

/**
 * Result of starting a trial: the new subscription plus a fresh access token that
 * now carries the {@code org_id} claim (the caller's old token has none). The
 * existing refresh token stays valid.
 */
public record StartTrialResponse(
        SubscriptionResponse subscription,
        String token,
        String tokenType,
        long expiresIn
) {
    public static StartTrialResponse of(SubscriptionResponse subscription, String token, long expiresIn) {
        return new StartTrialResponse(subscription, token, "Bearer", expiresIn);
    }
}
