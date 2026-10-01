package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.AmcArchiveRequest;
import com.example.cleancarsapi.dto.AmcPlanRenameRequest;
import com.example.cleancarsapi.dto.AmcPlanRequest;
import com.example.cleancarsapi.dto.AmcPlanSalesResponse;
import com.example.cleancarsapi.dto.AmcPlanResponse;
import com.example.cleancarsapi.dto.AmcVariantRequest;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.AmcPlanCreateService;
import com.example.cleancarsapi.service.AmcPlanDeleteService;
import com.example.cleancarsapi.service.AmcPlanReadService;
import com.example.cleancarsapi.service.AmcPlanSalesService;
import com.example.cleancarsapi.service.AmcPlanUpdateService;
import com.example.cleancarsapi.service.AmcVariantCreateService;
import com.example.cleancarsapi.service.AmcVariantUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * AMC plan templates and their variants (docs/FEATURE-AMC.md). Reads are open to the whole org;
 * writes need owner/manager. Every endpoint needs a subscription plan that includes AMC
 * (409 {@code amc_not_in_plan}). Every write returns the full updated plan.
 */
@RestController
@RequestMapping("/api/amc-plans")
@RequiredArgsConstructor
public class AmcPlanController {

    private final AmcPlanCreateService createService;
    private final AmcPlanReadService readService;
    private final AmcPlanUpdateService updateService;
    private final AmcVariantCreateService variantCreateService;
    private final AmcVariantUpdateService variantUpdateService;
    private final AmcPlanDeleteService deleteService;
    private final AmcPlanSalesService salesService;

    @GetMapping
    public PageResponse<AmcPlanResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, includeArchived, pageable);
    }

    @GetMapping("/{id}")
    public AmcPlanResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    /** How the plan has sold: totals, per-variant totals, and the sales still in force (vehicles mid-way). */
    @GetMapping("/{id}/sales")
    public AmcPlanSalesResponse sales(@PathVariable UUID id) {
        return salesService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AmcPlanResponse create(@Valid @RequestBody AmcPlanRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public AmcPlanResponse rename(@PathVariable UUID id, @Valid @RequestBody AmcPlanRenameRequest request) {
        return updateService.rename(AuthContext.requireOrgId(), id, request);
    }

    @PatchMapping("/{id}/archived")
    public AmcPlanResponse archive(@PathVariable UUID id, @RequestBody AmcArchiveRequest request) {
        return updateService.setArchived(AuthContext.requireOrgId(), id, request.archived());
    }

    @PostMapping("/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public AmcPlanResponse createVariant(@PathVariable UUID id, @Valid @RequestBody AmcVariantRequest request) {
        return variantCreateService.create(AuthContext.requireOrgId(), id, request);
    }

    @PutMapping("/{id}/variants/{variantId}")
    public AmcPlanResponse updateVariant(@PathVariable UUID id, @PathVariable UUID variantId,
                                         @Valid @RequestBody AmcVariantRequest request) {
        return variantUpdateService.update(AuthContext.requireOrgId(), id, variantId, request);
    }

    @PatchMapping("/{id}/variants/{variantId}/archived")
    public AmcPlanResponse archiveVariant(@PathVariable UUID id, @PathVariable UUID variantId,
                                          @RequestBody AmcArchiveRequest request) {
        return variantUpdateService.setArchived(AuthContext.requireOrgId(), id, variantId, request.archived());
    }

    /** Only a plan that was never sold can be deleted (409 {@code amc_plan_sold}); otherwise archive it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.deletePlan(AuthContext.requireOrgId(), id);
    }

    /** Only a never-sold variant can be deleted (409 {@code amc_variant_sold}); returns the plan afterwards. */
    @DeleteMapping("/{id}/variants/{variantId}")
    public AmcPlanResponse deleteVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        return deleteService.deleteVariant(AuthContext.requireOrgId(), id, variantId);
    }
}
