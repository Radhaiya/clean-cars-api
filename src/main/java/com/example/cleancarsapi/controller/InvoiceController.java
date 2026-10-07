package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.InvoiceRequest;
import com.example.cleancarsapi.dto.InvoiceResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.InvoiceService;
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

/** The invoice of a service order — {@code 404} on GET until it has been created. */
@RestController
@RequestMapping("/api/service-orders/{orderId}/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    public InvoiceResponse get(@PathVariable UUID orderId) {
        return invoiceService.get(AuthContext.requireOrgId(), orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@PathVariable UUID orderId, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(AuthContext.requireOrgId(), orderId, request);
    }

    @PutMapping
    public InvoiceResponse update(@PathVariable UUID orderId, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(AuthContext.requireOrgId(), orderId, request);
    }
}
