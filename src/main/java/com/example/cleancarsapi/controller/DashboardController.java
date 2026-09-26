package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.DashboardResponse;
import com.example.cleancarsapi.dto.DashboardResponse.MonthlyFinancial;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/**
 * The org's home-dashboard snapshot — no date-range params, see {@link DashboardService} —
 * plus its monthly earnings-vs-expenses feed for the earnings chart's year picker.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse get() {
        return dashboardService.get(AuthContext.requireOrgId());
    }

    /** Month-by-month earnings vs expenses for {@code year} — the earnings chart's double bars. */
    @GetMapping("/earnings")
    public List<MonthlyFinancial> getEarnings(@RequestParam int year) {
        return dashboardService.getEarnings(AuthContext.requireOrgId(), year);
    }
}
