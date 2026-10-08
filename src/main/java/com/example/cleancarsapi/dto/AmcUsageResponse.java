package com.example.cleancarsapi.dto;

import java.util.List;

/**
 * Where every sold AMC's visit slots stand right now (no date range — runtime counts as of today, see
 * {@code AmcSlots}): {@code used} = slots redeemed, {@code remaining} = current + future slots not yet used
 * (work already paid for and still owed), {@code lapsed} = past slots nobody redeemed (paid, never done).
 * Totals cover every AMC ever sold; {@code byPlan} splits them per AMC plan (variants combined).
 */
public record AmcUsageResponse(int used, int remaining, int lapsed, List<PlanUsage> byPlan) {

    public record PlanUsage(String planName, int used, int remaining, int lapsed) {
    }
}
