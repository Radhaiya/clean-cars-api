package com.example.cleancarsapi.dto;

/** Sanity cap on odometer values (meters): 999,999 mi ≈ 1,609,342,656 m, the largest the UI lets through. */
public final class DistanceLimits {
    public static final int MAX_METERS = 1_610_000_000;

    private DistanceLimits() {
    }
}
