package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.AmcPlan;
import com.example.cleancarsapi.entity.AmcPlanItem;
import com.example.cleancarsapi.entity.AmcPlanVariant;
import com.example.cleancarsapi.entity.AmcVariantRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An AMC plan with its fixed service names and its variants. All money is derived on read from
 * the per-row price / tax: {@code bundle*} is one slot's bundle (Σ rows), {@code total*} is the
 * upfront price of the whole tenure (bundle × {@code totalSlots}).
 */
public record AmcPlanResponse(
        UUID id,
        String name,
        boolean archived,
        List<String> serviceNames,
        List<Variant> variants,
        LocalDateTime createdAt
) {
    public record Variant(
            UUID id,
            int tenureMonths,
            int intervalMonths,
            int totalSlots,
            boolean archived,
            List<Row> rows,
            BigDecimal bundleNet,
            BigDecimal bundleTax,
            BigDecimal bundleGross,
            BigDecimal totalNet,
            BigDecimal totalTax,
            BigDecimal totalGross
    ) {
    }

    /** {@code price} is per unit; {@code net/tax/gross} are the row's line totals ({@code price × quantity}, tax per row). */
    public record Row(
            String serviceName,
            int quantity,
            BigDecimal price,
            BigDecimal taxPercentage,
            boolean taxIncluded,
            BigDecimal net,
            BigDecimal tax,
            BigDecimal gross
    ) {
    }

    public static AmcPlanResponse from(AmcPlan plan, List<AmcPlanItem> items, List<AmcPlanVariant> variants,
                                       Map<UUID, List<AmcVariantRow>> rowsByVariant) {
        Map<UUID, String> names = items.stream()
                .collect(java.util.stream.Collectors.toMap(AmcPlanItem::getId, AmcPlanItem::getServiceName));
        List<Variant> out = variants.stream().map(v -> {
            List<AmcVariantRow> rows = rowsByVariant.getOrDefault(v.getId(), List.of());
            List<Row> rowDtos = items.stream()
                    .flatMap(item -> rows.stream().filter(r -> r.getPlanItemId().equals(item.getId())))
                    .map(r -> {
                        TaxBreakdown b = TaxBreakdown.of(r.getPrice(), r.getTaxPercentage(), r.isTaxIncluded())
                                .times(r.getQuantity());
                        return new Row(names.get(r.getPlanItemId()), r.getQuantity(), r.getPrice(), r.getTaxPercentage(),
                                r.isTaxIncluded(), b.net(), b.tax(), b.gross());
                    }).toList();
            TaxBreakdown bundle = rowDtos.stream()
                    .map(r -> new TaxBreakdown(r.net(), r.tax(), r.gross()))
                    .reduce(TaxBreakdown.zero(), TaxBreakdown::plus);
            TaxBreakdown total = bundle.times(v.totalSlots());
            return new Variant(v.getId(), v.getTenureMonths(), v.getIntervalMonths(), v.totalSlots(), v.isArchived(),
                    rowDtos, bundle.net(), bundle.tax(), bundle.gross(), total.net(), total.tax(), total.gross());
        }).toList();
        return new AmcPlanResponse(plan.getId(), plan.getName(), plan.isArchived(),
                items.stream().map(AmcPlanItem::getServiceName).toList(), out, plan.getCreatedAt());
    }
}
