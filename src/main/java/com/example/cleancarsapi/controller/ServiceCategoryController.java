package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceCategoryRequest;
import com.example.cleancarsapi.dto.ServiceCategoryResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.ServiceCategoryCreateService;
import com.example.cleancarsapi.service.ServiceCategoryDeleteService;
import com.example.cleancarsapi.service.ServiceCategoryReadService;
import com.example.cleancarsapi.service.ServiceCategoryUpdateService;
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

/**
 * CRUD for the caller's per-org service categories (groupings for the service
 * catalogue). Each operation delegates to its own service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/service-categories")
@RequiredArgsConstructor
public class ServiceCategoryController {

    private final ServiceCategoryCreateService createService;
    private final ServiceCategoryReadService readService;
    private final ServiceCategoryUpdateService updateService;
    private final ServiceCategoryDeleteService deleteService;

    @GetMapping
    public PageResponse<ServiceCategoryResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, pageable);
    }

    @GetMapping("/{id}")
    public ServiceCategoryResponse get(@PathVariable long id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCategoryResponse create(@Valid @RequestBody ServiceCategoryRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public ServiceCategoryResponse update(@PathVariable long id, @Valid @RequestBody ServiceCategoryRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
