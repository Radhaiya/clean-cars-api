package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceCatalogRequest;
import com.example.cleancarsapi.dto.ServiceCatalogResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.ServiceCatalogCreateService;
import com.example.cleancarsapi.service.ServiceCatalogDeleteService;
import com.example.cleancarsapi.service.ServiceCatalogReadService;
import com.example.cleancarsapi.service.ServiceCatalogUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * CRUD for the caller's per-org service catalogue (the price list). Each operation
 * delegates to its own service — see docs/ARCHITECTURE.md. Only the base price and
 * GST inputs are stored; net/GST/gross are computed on read.
 */
@RestController
@RequestMapping("/api/service-catalog")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final ServiceCatalogCreateService createService;
    private final ServiceCatalogReadService readService;
    private final ServiceCatalogUpdateService updateService;
    private final ServiceCatalogDeleteService deleteService;

    @GetMapping
    public PageResponse<ServiceCatalogResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, pageable);
    }

    @GetMapping("/{id}")
    public ServiceCatalogResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCatalogResponse create(@Valid @RequestBody ServiceCatalogRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public ServiceCatalogResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceCatalogRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
