package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.BikeBrandRequest;
import com.example.cleancarsapi.dto.BikeBrandResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.BikeBrandCreateService;
import com.example.cleancarsapi.service.BikeBrandDeleteService;
import com.example.cleancarsapi.service.BikeBrandReadService;
import com.example.cleancarsapi.service.BikeBrandUpdateService;
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
 * CRUD for the caller's per-org bike brands. Each operation delegates to its own
 * service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/bike-brands")
@RequiredArgsConstructor
public class BikeBrandController {

    private final BikeBrandCreateService createService;
    private final BikeBrandReadService readService;
    private final BikeBrandUpdateService updateService;
    private final BikeBrandDeleteService deleteService;

    @GetMapping
    public PageResponse<BikeBrandResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, pageable);
    }

    @GetMapping("/{id}")
    public BikeBrandResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BikeBrandResponse create(@Valid @RequestBody BikeBrandRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public BikeBrandResponse update(@PathVariable UUID id, @Valid @RequestBody BikeBrandRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
