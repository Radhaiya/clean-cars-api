package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.AmcRedeemRequest;
import com.example.cleancarsapi.dto.AmcSaleRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.dto.AmcSubscriptionResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.AmcRedemptionService;
import com.example.cleancarsapi.service.AmcSubscriptionCreateService;
import com.example.cleancarsapi.service.AmcSubscriptionReadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sold AMCs (docs/FEATURE-AMC.md). Selling and reading are open to everyone in the org; a sale is
 * final (no update / cancel / refund). Counts are computed on every read.
 */
@RestController
@RequestMapping("/api/amc-subscriptions")
@RequiredArgsConstructor
public class AmcSubscriptionController {

    private final AmcSubscriptionCreateService createService;
    private final AmcSubscriptionReadService readService;
    private final AmcRedemptionService redemptionService;

    /** A vehicle's AMCs — pass exactly one of {@code carId} / {@code bikeId}. */
    @GetMapping
    public List<AmcSubscriptionResponse> list(@RequestParam(required = false) UUID carId,
                                              @RequestParam(required = false) UUID bikeId) {
        return readService.listForVehicle(AuthContext.requireOrgId(), carId, bikeId);
    }

    @GetMapping("/{id}")
    public AmcSubscriptionResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AmcSubscriptionResponse sell(@Valid @RequestBody AmcSaleRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    /**
     * Redeem the current period's bundle: creates a ₹0 service order for the AMC's vehicle with the
     * bundle at a hard-set 100% discount. 409 {@code amc_slot_used} / {@code amc_expired} / {@code amc_not_started}.
     */
    @PostMapping("/{id}/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceOrderResponse redeem(@PathVariable UUID id, @RequestBody AmcRedeemRequest request) {
        return redemptionService.redeem(AuthContext.requireOrgId(), id, request);
    }
}
