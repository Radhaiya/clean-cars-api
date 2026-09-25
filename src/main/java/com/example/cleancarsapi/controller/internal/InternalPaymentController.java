package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.internal.InternalPaymentEventResponse;
import com.example.cleancarsapi.dto.internal.InternalPaymentResponse;
import com.example.cleancarsapi.service.internal.PaymentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Razorpay payment history + lifecycle webhook audit for the internal console. */
@RestController
@RequestMapping("/internal/api/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentReadService paymentReadService;

    @GetMapping
    public PageResponse<InternalPaymentResponse> list(
            @RequestParam(required = false) UUID orgId,
            @PageableDefault(size = 20) Pageable pageable) {
        return paymentReadService.search(orgId, pageable);
    }

    @GetMapping("/events")
    public PageResponse<InternalPaymentEventResponse> events(
            @RequestParam(required = false) UUID orgId,
            @PageableDefault(size = 20) Pageable pageable) {
        return paymentReadService.events(orgId, pageable);
    }
}
