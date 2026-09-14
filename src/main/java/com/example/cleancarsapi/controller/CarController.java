package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.CarAndServicesResponse;
import com.example.cleancarsapi.dto.CarRequest;
import com.example.cleancarsapi.dto.CarResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.CarCreateService;
import com.example.cleancarsapi.service.CarDeleteService;
import com.example.cleancarsapi.service.CarReadService;
import com.example.cleancarsapi.service.CarUpdateService;
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
 * CRUD for the caller's per-org cars. List can be filtered by {@code customerId}
 * and a {@code search} on the plate number. Each operation delegates to its own
 * service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
public class CarController {

    private final CarCreateService createService;
    private final CarReadService readService;
    private final CarUpdateService updateService;
    private final CarDeleteService deleteService;

    @GetMapping
    public PageResponse<CarResponse> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "carNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), customerId, search, pageable);
    }

    /** Car detail — includes the car's past service orders (newest first) for drill-in by id. */
    @GetMapping("/{id}")
    public CarAndServicesResponse get(@PathVariable long id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarResponse create(@Valid @RequestBody CarRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public CarResponse update(@PathVariable long id, @Valid @RequestBody CarRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
