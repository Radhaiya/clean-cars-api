package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.PlanResponse;
import com.example.cleancarsapi.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The plan catalogue for the pricing / upgrade screen. Global (not org-scoped);
 * still behind auth like every non-{@code /api/auth} route. Read-only.
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public List<PlanResponse> list() {
        return planService.listPublic();
    }
}
