package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.DashboardResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The org's home-dashboard snapshot — no date-range params, see {@link DashboardService}. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse get() {
        return dashboardService.get(AuthContext.requireOrgId());
    }
}
