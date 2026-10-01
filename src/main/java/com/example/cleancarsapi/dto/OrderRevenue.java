package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.PaymentType;
import com.example.cleancarsapi.entity.ServiceOrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One order's money position, the unit all revenue reporting (charts, kpi-tiles, dashboard)
 * is built on. {@code net} / {@code gross} are summed from its lines; {@code amountPaid} is what
 * its payments add up to. The order's net revenue is split by how much of it has been
 * received: {@code paidNet = net × min(1, amountPaid / gross)} — so a half-paid split order
 * counts half its net as paid and half as unpaid, at the same tax ratio as the whole order.
 * Internal — never returned to clients as-is.
 */
public record OrderRevenue(
        LocalDateTime createdAt,
        UUID employeeId,
        ServiceOrderStatus status,
        BigDecimal net,
        BigDecimal gross,
        BigDecimal amountPaid,
        List<Receipt> payments
) {
    /** One payment's amount and method — {@code paymentType} is null for legacy migrated orders. */
    public record Receipt(PaymentType paymentType, BigDecimal amount) {
    }

    public BigDecimal paidNet() {
        if (gross.signum() == 0 || amountPaid.signum() == 0) {
            return zero();
        }
        return net.multiply(amountPaid.min(gross)).divide(gross, 2, RoundingMode.HALF_UP);
    }

    /** Net revenue still to be collected; voided (cancelled) orders owe nothing. */
    public BigDecimal unpaidNet() {
        return status == ServiceOrderStatus.CANCELLED ? zero() : net.subtract(paidNet());
    }

    /** Gross (tax-inclusive) amount still to be collected; voided (cancelled) orders owe nothing. */
    public BigDecimal unpaidGross() {
        return status == ServiceOrderStatus.CANCELLED ? zero() : gross.subtract(amountPaid).max(zero());
    }

    /**
     * {@link #paidNet()} apportioned to each payment method by its share of the money received;
     * the last entry absorbs the rounding so the parts always add up to {@code paidNet()}.
     */
    public Map<PaymentType, BigDecimal> paidNetByType() {
        Map<PaymentType, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal paidNet = paidNet();
        if (paidNet.signum() == 0) {
            return byType;
        }
        BigDecimal assigned = zero();
        for (int i = 0; i < payments.size(); i++) {
            Receipt r = payments.get(i);
            BigDecimal share = i == payments.size() - 1
                    ? paidNet.subtract(assigned)
                    : paidNet.multiply(r.amount()).divide(amountPaid, 2, RoundingMode.HALF_UP);
            assigned = assigned.add(share);
            byType.merge(r.paymentType(), share, BigDecimal::add);
        }
        return byType;
    }

    private static BigDecimal zero() {
        return TaxBreakdown.zero().net();
    }
}
