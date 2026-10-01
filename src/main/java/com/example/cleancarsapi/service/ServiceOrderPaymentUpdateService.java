package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.PaymentRequest;
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

/**
 * UPDATE half of the payments sub-resource. The amount is re-validated against what the
 * <em>other</em> payments leave, so a split can be raised up to the remaining balance plus
 * its own old amount; a one-time payment always re-snaps to the order's current total.
 */
@Service
@RequiredArgsConstructor
public class ServiceOrderPaymentUpdateService {

    private final ServiceOrderRepository orders;
    private final PaymentRepository payments;
    private final ServiceOrderPaymentLedger ledger;
    private final ServiceOrderAssembler assembler;

    @Transactional
    public ServiceOrderResponse update(UUID orgId, UUID orderId, UUID paymentId, PaymentRequest request) {
        ServiceOrder order = orders.lockByIdAndOrgId(orderId, orgId)
                .orElseThrow(() -> new NotFoundException("service order", orderId));
        Payment payment = payments.findByIdAndServiceOrderIdAndOrgId(paymentId, orderId, orgId)
                .orElseThrow(() -> new NotFoundException("payment", paymentId));

        payment.setAmount(ledger.resolveAmount(order, request.amount(), order.getAmountPaid().subtract(payment.getAmount())));
        payment.setPaymentType(request.paymentType());
        if (request.paymentDate() != null) {
            payment.setPaymentDate(request.paymentDate());
        }
        payments.save(payment);

        ledger.refresh(order);
        return assembler.toResponse(orgId, order);
    }
}
