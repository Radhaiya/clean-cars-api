package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.AmcSubscription;
import com.example.cleancarsapi.entity.AmcSubscriptionItem;
import com.example.cleancarsapi.entity.PaymentType;
import com.example.cleancarsapi.service.AmcSlots;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A sold AMC with its runtime counts. {@code total/used/lapsed/remaining/availableNow/currentSlot/status}
 * are computed on every read from the start date, tenure and redemption orders — never stored.
 * {@code sale*} is the whole-tenure upfront price (Σ rows × slots, tax per row).
 */
public record AmcSubscriptionResponse(
        UUID id,
        UUID carId,
        UUID bikeId,
        String planName,
        int tenureMonths,
        int intervalMonths,
        LocalDate startDate,
        LocalDate endDate,
        AmcSlots.Status status,
        int total,
        int used,
        int lapsed,
        int remaining,
        int availableNow,
        Integer currentSlot,
        List<AmcPlanResponse.Row> rows,
        BigDecimal bundleGross,
        BigDecimal saleNet,
        BigDecimal saleTax,
        BigDecimal saleGross,
        PaymentType paymentType,
        LocalDate paymentDate,
        UUID soldByEmployeeId,
        String soldByName,
        LocalDateTime createdAt,
        boolean hasInvoice
) {
    public static AmcSubscriptionResponse from(AmcSubscription s, List<AmcSubscriptionItem> items, AmcSlots.Counts c,
                                               String soldByName, boolean hasInvoice) {
        List<AmcPlanResponse.Row> rows = items.stream().map(i -> {
            TaxBreakdown b = TaxBreakdown.of(i.getPrice(), i.getTaxPercentage(), i.isTaxIncluded())
                    .times(i.getQuantity());
            return new AmcPlanResponse.Row(i.getServiceName(), i.getQuantity(), i.getPrice(), i.getTaxPercentage(), i.isTaxIncluded(),
                    b.net(), b.tax(), b.gross());
        }).toList();
        BigDecimal bundleGross = rows.stream().map(AmcPlanResponse.Row::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AmcSubscriptionResponse(s.getId(), s.getCarId(), s.getBikeId(), s.getPlanName(),
                s.getTenureMonths(), s.getIntervalMonths(), s.getStartDate(), c.endDate(), c.status(),
                c.total(), c.used(), c.lapsed(), c.remaining(), c.availableNow(), c.currentSlot(),
                rows, bundleGross, s.getSaleNet(), s.getSaleTax(), s.getSaleGross(),
                s.getPaymentType(), s.getPaymentDate(), s.getSoldByEmployeeId(), soldByName, s.getCreatedAt(),
                hasInvoice);
    }
}
