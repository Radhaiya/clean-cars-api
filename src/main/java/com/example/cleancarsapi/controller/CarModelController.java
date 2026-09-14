package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.CarModelRequest;
import com.example.cleancarsapi.dto.CarModelResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.CarModelCreateService;
import com.example.cleancarsapi.service.CarModelDeleteService;
import com.example.cleancarsapi.service.CarModelReadService;
import com.example.cleancarsapi.service.CarModelUpdateService;
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
 * CRUD for the caller's per-org car models. List can be filtered by {@code brandId}.
 * Each operation delegates to its own service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/car-models")
@RequiredArgsConstructor
public class CarModelController {

    private final CarModelCreateService createService;
    private final CarModelReadService readService;
    private final CarModelUpdateService updateService;
    private final CarModelDeleteService deleteService;

    @GetMapping
    public PageResponse<CarModelResponse> list(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), brandId, search, pageable);
    }

    @GetMapping("/{id}")
    public CarModelResponse get(@PathVariable long id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarModelResponse create(@Valid @RequestBody CarModelRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public CarModelResponse update(@PathVariable long id, @Valid @RequestBody CarModelRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
