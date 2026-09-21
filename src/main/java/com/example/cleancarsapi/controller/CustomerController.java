package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.CustomerAndCarsResponse;
import com.example.cleancarsapi.dto.CustomerRequest;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.CustomerCreateService;
import com.example.cleancarsapi.service.CustomerDeleteService;
import com.example.cleancarsapi.service.CustomerReadService;
import com.example.cleancarsapi.service.CustomerUpdateService;
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
 * CRUD for customers, always scoped to the caller's organization
 * ({@code orgId} comes from the Bearer token, never the request).
 * Each operation is delegated to its own service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerCreateService createService;
    private final CustomerReadService readService;
    private final CustomerUpdateService updateService;
    private final CustomerDeleteService deleteService;

    @GetMapping
    public PageResponse<CustomerResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, pageable);
    }

    @GetMapping("/{id}")
    public CustomerAndCarsResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
