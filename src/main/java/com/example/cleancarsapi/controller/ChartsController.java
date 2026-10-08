package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.AmcExpiringResponse;
import com.example.cleancarsapi.dto.AmcUsageResponse;
import com.example.cleancarsapi.dto.ChartBreakdownRow;
import com.example.cleancarsapi.dto.ChartBucket;
import com.example.cleancarsapi.dto.ChartGranularity;
import com.example.cleancarsapi.dto.ChartMetric;
import com.example.cleancarsapi.dto.KpiTilesResponse;
import com.example.cleancarsapi.dto.OrgTotalsResponse;
import com.example.cleancarsapi.dto.TopCustomerResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.AmcInsightsService;
import com.example.cleancarsapi.service.ChartService;
import com.example.cleancarsapi.service.TopCustomersService;
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
    private final AmcInsightsService amcInsights;
    private final TopCustomersService topCustomers;

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
     * data, not mocked: net-of-tax revenue, expenses, and profit for the range.
     * Same {@code stats_range_years} plan gating as {@link #get}.
     */
    @GetMapping("/kpi-tiles")
    public KpiTilesResponse getKpiTiles(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return chartService.getKpiTiles(AuthContext.requireOrgId(), from, to);
    }

    /** Per service name: units performed + net money received, non-cancelled non-AMC orders in range. Same plan gating. */
    @GetMapping("/services")
    public List<ChartBreakdownRow> getServiceBreakdown(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return chartService.getServiceBreakdown(AuthContext.requireOrgId(), from, to);
    }

    /** Per AMC plan: count sold + net sale amount collected (payment date in range, not future). Same plan gating. */
    @GetMapping("/amcs")
    public List<ChartBreakdownRow> getAmcBreakdown(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return chartService.getAmcBreakdown(AuthContext.requireOrgId(), from, to);
    }

    /** AMC visit slots used / remaining / lapsed as of today (no date range), total and per plan. Needs {@code amc_enabled}. */
    @GetMapping("/amc-usage")
    public AmcUsageResponse getAmcUsage() {
        return amcInsights.usage(AuthContext.requireOrgId());
    }

    /** Active AMCs ending within the next 30 days (no date range), soonest first — the renewal list. Needs {@code amc_enabled}. */
    @GetMapping("/amc-expiring")
    public List<AmcExpiringResponse> getAmcExpiring() {
        return amcInsights.expiringSoon(AuthContext.requireOrgId());
    }

    /** Top 10 customers by lifetime net revenue (service orders + AMCs on their vehicles) — no date range, not plan-gated. */
    @GetMapping("/top-customers")
    public List<TopCustomerResponse> getTopCustomers() {
        return topCustomers.top(AuthContext.requireOrgId());
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
