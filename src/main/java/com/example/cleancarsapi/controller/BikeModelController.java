package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.BikeModelRequest;
import com.example.cleancarsapi.dto.BikeModelResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.BikeModelCreateService;
import com.example.cleancarsapi.service.BikeModelDeleteService;
import com.example.cleancarsapi.service.BikeModelReadService;
import com.example.cleancarsapi.service.BikeModelUpdateService;
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
 * CRUD for the caller's per-org bike models. List can be filtered by {@code brandId}.
 * Each operation delegates to its own service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/bike-models")
@RequiredArgsConstructor
public class BikeModelController {

    private final BikeModelCreateService createService;
    private final BikeModelReadService readService;
    private final BikeModelUpdateService updateService;
    private final BikeModelDeleteService deleteService;

    @GetMapping
    public PageResponse<BikeModelResponse> list(
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), brandId, search, pageable);
    }

    @GetMapping("/{id}")
    public BikeModelResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BikeModelResponse create(@Valid @RequestBody BikeModelRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public BikeModelResponse update(@PathVariable UUID id, @Valid @RequestBody BikeModelRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
