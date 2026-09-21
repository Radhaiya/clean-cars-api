package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.ChartBucket;
import com.example.cleancarsapi.dto.ChartGranularity;
import com.example.cleancarsapi.dto.ChartMetric;
import com.example.cleancarsapi.dto.KpiTilesResponse;
import com.example.cleancarsapi.dto.OrgTotalsResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.ChartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
/** Time-bucketed metrics for the dashboard charts. */
@RestController
@RequestMapping("/api/charts")
@RequiredArgsConstructor
public class ChartsController {

    private final ChartService chartService;

    /**
     * Buckets {@code metric} into {@code granularity}-sized, calendar-aligned periods
     * covering {@code [from, to]} (both inclusive). Gated by the org's
     * {@code stats_range_years} plan limit — {@code 409 statistics_not_available} if the
     * plan hides the stats page (or there's no live subscription), {@code 409
     * stats_range_exceeded} if {@code from} reaches further back than the plan allows.
     */
    @GetMapping
    public List<ChartBucket> get(
            @RequestParam ChartMetric metric,
            @RequestParam ChartGranularity granularity,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return chartService.getBuckets(AuthContext.requireOrgId(), metric, granularity, from, to);
    }

    /**
     * The dashboard's P&amp;L tile for {@code [from, to]} (both inclusive) — real
     * data, not mocked: net-of-GST revenue, expenses, and profit for the range.
     * Same {@code stats_range_years} plan gating as {@link #get}.
     */
    @GetMapping("/kpi-tiles")
    public KpiTilesResponse getKpiTiles(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return chartService.getKpiTiles(AuthContext.requireOrgId(), from, to);
    }

    /**
     * All-time org counts — no date range, no {@code stats_range_years} gating
     * (unlike {@link #get} / {@link #getKpiTiles}, these are basic counts, not
     * statistics history).
     */
    @GetMapping("/totals")
    public OrgTotalsResponse getTotals() {
        return chartService.getTotals(AuthContext.requireOrgId());
    }
}
