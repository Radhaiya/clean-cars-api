package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.ServiceOrderPaymentLine;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.entity.Payment;
import com.example.cleancarsapi.entity.PaymentPlan;
import com.example.cleancarsapi.entity.ServiceOrder;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.PaymentRepository;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single home of the payment rules, shared by both plans (they differ only in
 * {@link #resolveAmount}). Also keeps the order's denormalized {@code amountPaid} / {@code paid}
 * in step with its {@code payments} — call {@link #refresh} after anything that changes either
 * side of that comparison: a payment added/edited/removed, or the order's lines replaced.
 */
@Component
@RequiredArgsConstructor
public class ServiceOrderPaymentLedger {

    private final ServiceOrderRepository orders;
    private final ServiceOrderItemRepository items;
    private final PaymentRepository payments;
    private final OrgTimeZoneResolver orgTimezones;

    /** The order's gross total (incl. tax), summed from its current lines. */
    public BigDecimal grossTotal(UUID orderId) {
        BigDecimal total = TaxBreakdown.zero().gross();
        for (ServiceOrderItem item : items.findByServiceOrderIdOrderByCreatedAtAscIdAsc(orderId)) {
            total = total.add(TaxBreakdown.ofLine(item).gross());
        }
        return total;
    }

    /**
     * The amount a payment on this order actually records.
     * <ul>
     *   <li>{@code ONE_TIME}: always the full order total, and only when nothing else is paid
     *       ({@code otherPaid} is the sum of the order's <em>other</em> payments).</li>
     *   <li>{@code SPLIT}: the requested amount, which is required and may not exceed what is
     *       left after {@code otherPaid}.</li>
     * </ul>
     */
    public BigDecimal resolveAmount(ServiceOrder order, BigDecimal requested, BigDecimal otherPaid) {
        requireNotAmcRedemption(order);
        BigDecimal gross = grossTotal(order.getId());
        if (order.getPaymentPlan() == PaymentPlan.ONE_TIME) {
            if (otherPaid.signum() > 0) {
                throw ConflictException.oneTimeAlreadyPaid();
            }
            if (gross.signum() == 0) {
                throw ConflictException.orderTotalZero();
            }
            return gross;
        }
        if (requested == null) {
            throw new BadRequestException("amount is required for a split payment");
        }
        BigDecimal amount = requested.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = gross.subtract(otherPaid);
        if (amount.compareTo(remaining) > 0) {
            throw ConflictException.paymentExceedsRemaining(remaining.max(BigDecimal.ZERO));
        }
        return amount;
    }

    /** Switch plans. Going back to one-time is only possible while the order holds no payments. */
    public void changePlan(ServiceOrder order, PaymentPlan plan) {
        if (plan != null && plan != order.getPaymentPlan()) {
            requireNotAmcRedemption(order);
        }
        if (plan == null || plan == order.getPaymentPlan()) {
            return;
        }
        if (plan == PaymentPlan.ONE_TIME && payments.existsByServiceOrderId(order.getId())) {
            throw ConflictException.paymentPlanHasPayments();
        }
        order.setPaymentPlan(plan);
    }

    /**
     * Replace the order's whole payment set (the create/update-order form). Lines with a known
     * {@code id} are updated in place, others created, and existing payments not listed are
     * removed. Validated against the order's plan and current lines — call after the lines are saved.
     */
    public void replacePayments(ServiceOrder order, List<ServiceOrderPaymentLine> lines) {
        if (!lines.isEmpty()) {
            requireNotAmcRedemption(order);
        }
        Map<UUID, Payment> existing = new HashMap<>();
        for (Payment p : payments.findByServiceOrderIdOrderByPaymentDateAscCreatedAtAscIdAsc(order.getId())) {
            existing.put(p.getId(), p);
        }
        BigDecimal gross = grossTotal(order.getId());
        boolean oneTime = order.getPaymentPlan() == PaymentPlan.ONE_TIME;
        if (oneTime && lines.size() > 1) {
            throw ConflictException.oneTimeAlreadyPaid();
        }
        if (oneTime && !lines.isEmpty() && gross.signum() == 0) {
            throw ConflictException.orderTotalZero();
        }

        BigDecimal running = BigDecimal.ZERO;
        List<Payment> kept = new java.util.ArrayList<>();
        for (ServiceOrderPaymentLine line : lines) {
            BigDecimal amount;
            if (oneTime) {
                amount = gross;
            } else {
                if (line.amount() == null) {
                    throw new BadRequestException("amount is required for a split payment");
                }
                amount = line.amount().setScale(2, RoundingMode.HALF_UP);
                if (running.add(amount).compareTo(gross) > 0) {
                    throw ConflictException.paymentExceedsRemaining(gross.subtract(running).max(BigDecimal.ZERO));
                }
            }
            running = running.add(amount);

            Payment payment;
            if (line.id() != null) {
                payment = existing.remove(line.id());
                if (payment == null) {
                    throw new NotFoundException("payment", line.id());
                }
            } else {
                payment = new Payment();
                payment.setOrgId(order.getOrgId());
                payment.setServiceOrderId(order.getId());
                payment.setReceivedBy(AuthContext.require().userId());
            }
            payment.setAmount(amount);
            payment.setPaymentType(line.paymentType());
            payment.setPaymentDate(line.paymentDate() != null ? line.paymentDate() : orgTimezones.now().toLocalDate());
            kept.add(payment);
        }
        payments.deleteAll(existing.values());
        payments.flush();
        payments.saveAll(kept);
        refresh(order);
    }

    /** An AMC redemption is free: it takes no payments and never changes plan. */
    private static void requireNotAmcRedemption(ServiceOrder order) {
        if (order.isAmcRedemption()) {
            throw ConflictException.amcOrderLocked();
        }
    }

    /** Recompute {@code amountPaid} and the derived {@code paid} flag from the ledger and the order's current lines. */
    public void refresh(ServiceOrder order) {
        payments.flush();
        BigDecimal paid = payments.findByServiceOrderIdOrderByPaymentDateAscCreatedAtAscIdAsc(order.getId()).stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setAmountPaid(paid);
        BigDecimal gross = grossTotal(order.getId());
        // A zero-total order (e.g. a 100% discount / AMC redemption) has nothing to receive: it counts as paid once it has lines.
        boolean settledFree = gross.signum() == 0 && items.existsByServiceOrderId(order.getId());
        order.setPaid(settledFree || (paid.signum() > 0 && paid.compareTo(gross) >= 0));
        orders.save(order);
    }
}
