package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.Payment;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.PaymentRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** DELETE half of the payments sub-resource — removing the last payment makes the order unpaid again. */
@Service
@RequiredArgsConstructor
public class ServiceOrderPaymentDeleteService {

    private final ServiceOrderRepository orders;
    private final PaymentRepository payments;
    private final ServiceOrderPaymentLedger ledger;
    private final ServiceOrderAssembler assembler;

    @Transactional
    public ServiceOrderResponse delete(UUID orgId, UUID orderId, UUID paymentId) {
        ServiceOrder order = orders.lockByIdAndOrgId(orderId, orgId)
                .orElseThrow(() -> new NotFoundException("service order", orderId));
        Payment payment = payments.findByIdAndServiceOrderIdAndOrgId(paymentId, orderId, orgId)
                .orElseThrow(() -> new NotFoundException("payment", paymentId));
        payments.delete(payment);

        ledger.refresh(order);
        return assembler.toResponse(orgId, order);
    }
}
