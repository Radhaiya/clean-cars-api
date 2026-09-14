package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.VendorRequest;
import com.example.cleancarsapi.dto.VendorResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.VendorCreateService;
import com.example.cleancarsapi.service.VendorDeleteService;
import com.example.cleancarsapi.service.VendorReadService;
import com.example.cleancarsapi.service.VendorUpdateService;
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
 * CRUD for the caller's per-org vendors (outsourcing garages). Each operation
 * delegates to its own service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorCreateService createService;
    private final VendorReadService readService;
    private final VendorUpdateService updateService;
    private final VendorDeleteService deleteService;

    @GetMapping
    public PageResponse<VendorResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, pageable);
    }

    @GetMapping("/{id}")
    public VendorResponse get(@PathVariable long id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendorResponse create(@Valid @RequestBody VendorRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public VendorResponse update(@PathVariable long id, @Valid @RequestBody VendorRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
