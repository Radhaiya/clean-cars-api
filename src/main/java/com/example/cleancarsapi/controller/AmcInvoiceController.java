package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.AmcInvoiceRequest;
import com.example.cleancarsapi.dto.AmcInvoiceResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.AmcInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The invoice of an AMC sale — {@code 404} on GET until it has been created. */
@RestController
@RequestMapping("/api/amc-subscriptions/{subscriptionId}/invoice")
@RequiredArgsConstructor
public class AmcInvoiceController {

    private final AmcInvoiceService invoiceService;

    @GetMapping
    public AmcInvoiceResponse get(@PathVariable UUID subscriptionId) {
        return invoiceService.get(AuthContext.requireOrgId(), subscriptionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AmcInvoiceResponse create(@PathVariable UUID subscriptionId, @Valid @RequestBody AmcInvoiceRequest request) {
        return invoiceService.create(AuthContext.requireOrgId(), subscriptionId, request);
    }

    @PutMapping
    public AmcInvoiceResponse update(@PathVariable UUID subscriptionId, @Valid @RequestBody AmcInvoiceRequest request) {
        return invoiceService.update(AuthContext.requireOrgId(), subscriptionId, request);
    }
}
