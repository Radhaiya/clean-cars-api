package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.PaymentRequest;
import com.example.cleancarsapi.dto.ServiceOrderResponse;
import com.example.cleancarsapi.entity.Payment;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.PaymentRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** CREATE half of the payments sub-resource — records one payment against an order (either plan). */
@Service
@RequiredArgsConstructor
public class ServiceOrderPaymentCreateService {

    private final ServiceOrderRepository orders;
    private final PaymentRepository payments;
    private final ServiceOrderPaymentLedger ledger;
    private final ServiceOrderAssembler assembler;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional
    public ServiceOrderResponse create(UUID orgId, UUID orderId, PaymentRequest request) {
        ServiceOrder order = orders.lockByIdAndOrgId(orderId, orgId)
                .orElseThrow(() -> new NotFoundException("service order", orderId));

        Payment payment = new Payment();
        payment.setOrgId(orgId);
        payment.setServiceOrderId(orderId);
        payment.setAmount(ledger.resolveAmount(order, request.amount(), order.getAmountPaid()));
        payment.setPaymentType(request.paymentType());
        payment.setPaymentDate(request.paymentDate() != null ? request.paymentDate() : orgTimezones.now().toLocalDate());
        payment.setReceivedBy(AuthContext.require().userId());
        payments.save(payment);

        ledger.refresh(order);
        return assembler.toResponse(orgId, order);
    }
}
