package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.ServiceCatalogStatsResponse;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.entity.ServiceCatalog;
import com.example.cleancarsapi.entity.ServiceOrderItem;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.repository.ServiceOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Per-service usage numbers, embedded in {@code GET /api/service-catalog/{id}}. */
@Service
@RequiredArgsConstructor
public class ServiceCatalogStatsService {

    private final ServiceOrderItemRepository items;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public ServiceCatalogStatsResponse stats(UUID orgId, ServiceCatalog entry) {
        List<Object[]> lines = items.findLinesByServiceName(orgId, entry.getName(), ServiceOrderStatus.CANCELLED);

        YearMonth thisMonth = YearMonth.from(orgTimezones.now());
        Set<UUID> orderIds = new HashSet<>();
        long units = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal monthRevenue = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        LocalDate first = null;
        LocalDate last = null;
        for (Object[] row : lines) {
            ServiceOrderItem item = (ServiceOrderItem) row[0];
            LocalDate day = ((LocalDateTime) row[1]).toLocalDate();
            BigDecimal gross = TaxBreakdown.ofLine(item).gross();
            orderIds.add(item.getServiceOrderId());
            units += item.getQuantity();
            revenue = revenue.add(gross);
            if (YearMonth.from(day).equals(thisMonth)) {
                monthRevenue = monthRevenue.add(gross);
            }
            if (item.getDiscountAmount() != null) {
                discount = discount.add(item.getDiscountAmount().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            first = first == null || day.isBefore(first) ? day : first;
            last = last == null || day.isAfter(last) ? day : last;
        }
        BigDecimal average = orderIds.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(orderIds.size()), 2, RoundingMode.HALF_UP);
        return new ServiceCatalogStatsResponse(orderIds.size(), units, revenue, monthRevenue, average, discount, first, last);
    }
}
