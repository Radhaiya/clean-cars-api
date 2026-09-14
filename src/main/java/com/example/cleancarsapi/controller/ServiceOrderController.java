package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.ServiceOrderPaidRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.dto.ServiceOrderStatusRequest;
import com.example.cleancarsapi.dto.ServiceOrderSummaryResponse;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.ServiceOrderCreateService;
import com.example.cleancarsapi.service.ServiceOrderDeleteService;
import com.example.cleancarsapi.service.ServiceOrderReadService;
import com.example.cleancarsapi.service.ServiceOrderUpdateService;
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

/**
 * CRUD for service orders ("the service log"). Each operation delegates to its own
 * service — see docs/ARCHITECTURE.md. List rows are lightweight; {@code GET /{id}}
 * returns the lines and the computed totals.
 */
@RestController
@RequestMapping("/api/service-orders")
@RequiredArgsConstructor
public class ServiceOrderController {

    private final ServiceOrderCreateService createService;
    private final ServiceOrderReadService readService;
    private final ServiceOrderUpdateService updateService;
    private final ServiceOrderDeleteService deleteService;

    @GetMapping
    public PageResponse<ServiceOrderSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(required = false) Boolean paid,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, status, paid, pageable);
    }

    @GetMapping("/{id}")
    public ServiceOrderResponse get(@PathVariable long id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOrderResponse create(@Valid @RequestBody ServiceOrderRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public ServiceOrderResponse update(@PathVariable long id, @Valid @RequestBody ServiceOrderRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    /** Quick edit: flip paid/unpaid. Body: {@code {"paid": true}}. */
    @PatchMapping("/{id}/paid")
    public ServiceOrderResponse setPaid(@PathVariable long id, @Valid @RequestBody ServiceOrderPaidRequest request) {
        return updateService.setPaid(AuthContext.requireOrgId(), id, request.paid());
    }

    /** Quick edit: change the order status. Body: {@code {"status": "completed"}}. */
    @PatchMapping("/{id}/status")
    public ServiceOrderResponse setStatus(@PathVariable long id, @Valid @RequestBody ServiceOrderStatusRequest request) {
        return updateService.setStatus(AuthContext.requireOrgId(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
