package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.PaymentPlanRequest;
import com.example.cleancarsapi.dto.PaymentRequest;
import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.dto.ServiceOrderStatusRequest;
import com.example.cleancarsapi.dto.ServiceOrderSummaryResponse;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.repository.ServiceOrderSpecs;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.ServiceOrderCreateService;
import com.example.cleancarsapi.service.ServiceOrderDeleteService;
import com.example.cleancarsapi.service.ServiceOrderPaymentCreateService;
import com.example.cleancarsapi.service.ServiceOrderPaymentDeleteService;
import com.example.cleancarsapi.service.ServiceOrderPaymentUpdateService;
import com.example.cleancarsapi.service.ServiceOrderReadService;
import com.example.cleancarsapi.service.ServiceOrderUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
/**
 * CRUD for service orders ("the service log"). Each operation delegates to its own
 * service — see docs/ARCHITECTURE.md. List rows are lightweight; {@code GET /{id}}
 * returns the lines and the computed totals.
 */
@RestController
@RequestMapping("/api/service-orders")
@RequiredArgsConstructor
public class ServiceOrderController {

    static final int EXPORT_PAGE_SIZE = 200;

    private final ServiceOrderCreateService createService;
    private final ServiceOrderReadService readService;
    private final ServiceOrderUpdateService updateService;
    private final ServiceOrderDeleteService deleteService;
    private final ServiceOrderPaymentCreateService paymentCreateService;
    private final ServiceOrderPaymentUpdateService paymentUpdateService;
    private final ServiceOrderPaymentDeleteService paymentDeleteService;

    @GetMapping
    public PageResponse<ServiceOrderSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) String vehicle,
            @RequestParam(required = false) ServiceOrderSpecs.VehicleType vehicleType,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) Integer services,
            @RequestParam(required = false) BigDecimal totalMin,
            @RequestParam(required = false) BigDecimal totalMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, status, paid,
                vehicle, vehicleType, customerId, employeeId, services, totalMin, totalMax, from, to, pageable);
    }

    /**
     * Same filters and sort as {@link #list}, but pages are a fixed {@value #EXPORT_PAGE_SIZE} rows (outside
     * the global {@code max-page-size} cap) so the CSV export needs few round trips. Walk {@code page} until
     * {@code totalPages}.
     */
    @GetMapping("/export")
    public PageResponse<ServiceOrderSummaryResponse> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) String vehicle,
            @RequestParam(required = false) ServiceOrderSpecs.VehicleType vehicleType,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) Integer services,
            @RequestParam(required = false) BigDecimal totalMin,
            @RequestParam(required = false) BigDecimal totalMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page) {
        Sort order = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            order = Sort.by("desc".equalsIgnoreCase(parts.length > 1 ? parts[1].trim() : "")
                    ? Sort.Direction.DESC : Sort.Direction.ASC, parts[0].trim());
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), EXPORT_PAGE_SIZE, order);
        return readService.list(AuthContext.requireOrgId(), search, status, paid,
                vehicle, vehicleType, customerId, employeeId, services, totalMin, totalMax, from, to, pageable);
    }

    @GetMapping("/{id}")
    public ServiceOrderResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOrderResponse create(@Valid @RequestBody ServiceOrderRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public ServiceOrderResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceOrderRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    /** Quick edit: switch the order between {@code ONE_TIME} and {@code SPLIT}. */
    @PatchMapping("/{id}/payment-plan")
    public ServiceOrderResponse setPaymentPlan(@PathVariable UUID id, @Valid @RequestBody PaymentPlanRequest request) {
        return updateService.setPaymentPlan(AuthContext.requireOrgId(), id, request);
    }

    /** Record a payment. ONE_TIME: always the full total (body amount ignored). SPLIT: {@code amount} up to what remains. */
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOrderResponse addPayment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return paymentCreateService.create(AuthContext.requireOrgId(), id, request);
    }

    @PutMapping("/{id}/payments/{paymentId}")
    public ServiceOrderResponse updatePayment(@PathVariable UUID id, @PathVariable UUID paymentId,
                                              @Valid @RequestBody PaymentRequest request) {
        return paymentUpdateService.update(AuthContext.requireOrgId(), id, paymentId, request);
    }

    /** Remove a payment; returns the order with its recomputed paid / remaining. */
    @DeleteMapping("/{id}/payments/{paymentId}")
    public ServiceOrderResponse deletePayment(@PathVariable UUID id, @PathVariable UUID paymentId) {
        return paymentDeleteService.delete(AuthContext.requireOrgId(), id, paymentId);
    }

    /** Quick edit: change the order status. Body: {@code {"status": "completed"}}. */
    @PatchMapping("/{id}/status")
    public ServiceOrderResponse setStatus(@PathVariable UUID id, @Valid @RequestBody ServiceOrderStatusRequest request) {
        return updateService.setStatus(AuthContext.requireOrgId(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
