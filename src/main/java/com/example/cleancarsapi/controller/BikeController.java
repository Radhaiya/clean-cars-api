package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.BikeAndServicesResponse;
import com.example.cleancarsapi.dto.BikeRequest;
import com.example.cleancarsapi.dto.BikeResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.BikeCreateService;
import com.example.cleancarsapi.service.BikeDeleteService;
import com.example.cleancarsapi.service.BikeReadService;
import com.example.cleancarsapi.service.BikeUpdateService;
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
 * CRUD for the caller's per-org bikes. List can be filtered by {@code customerId}
 * and a {@code search} on the registration number. Each operation delegates to its
 * own service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/bikes")
@RequiredArgsConstructor
public class BikeController {

    private final BikeCreateService createService;
    private final BikeReadService readService;
    private final BikeUpdateService updateService;
    private final BikeDeleteService deleteService;

    @GetMapping
    public PageResponse<BikeResponse> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "bikeNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), customerId, search, pageable);
    }

    /** Bike detail — includes the bike's past service orders (newest first) for drill-in by id. */
    @GetMapping("/{id}")
    public BikeAndServicesResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BikeResponse create(@Valid @RequestBody BikeRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public BikeResponse update(@PathVariable UUID id, @Valid @RequestBody BikeRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
