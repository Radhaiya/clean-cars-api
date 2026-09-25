package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.internal.InternalOrgDetailResponse;
import com.example.cleancarsapi.dto.internal.InternalOrgSummaryResponse;
import com.example.cleancarsapi.dto.internal.InternalOrgTotalsResponse;
import com.example.cleancarsapi.service.internal.OrgReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Org views for the internal console (global, not org-scoped). */
@RestController
@RequestMapping("/internal/api/orgs")
@RequiredArgsConstructor
public class InternalOrgController {

    private final OrgReadService orgReadService;

    @GetMapping
    public PageResponse<InternalOrgSummaryResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(orgReadService.list(search, pageable));
    }

    @GetMapping("/{id}")
    public InternalOrgDetailResponse detail(@PathVariable java.util.UUID id) {
        return orgReadService.detail(id);
    }

    @GetMapping("/{id}/stats")
    public InternalOrgTotalsResponse stats(@PathVariable java.util.UUID id) {
        return orgReadService.stats(id);
    }
}
