package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ChartRevenueLine;
import com.example.cleancarsapi.dto.DashboardResponse;
import com.example.cleancarsapi.dto.DashboardResponse.MonthlyEarning;
import com.example.cleancarsapi.dto.DashboardResponse.RevenueSplit;
import com.example.cleancarsapi.dto.GstBreakdown;
import com.example.cleancarsapi.entity.ServiceOrderStatus;
import com.example.cleancarsapi.repository.ServiceOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/**
 * The org's home-dashboard snapshot (single class, not a CRUD resource, not gated by
 * any plan limit — unlike the {@code stats_range_years}-gated charts/KPI endpoints,
 * this is the always-available operational overview). See {@link DashboardResponse}.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ServiceOrderRepository serviceOrders;

    @Transactional(readOnly = true)
    public DashboardResponse get(UUID orgId) {
        long servicesInProgress = serviceOrders.countByOrgIdAndStatus(orgId, ServiceOrderStatus.IN_PROGRESS);
        long servicesUnpaid = serviceOrders.countByOrgIdAndPaidFalseAndStatusNot(orgId, ServiceOrderStatus.CANCELLED);

        LocalDate today = LocalDate.now();
        RevenueSplit todayRevenue = revenueSplit(orgId, today);
        RevenueSplit yesterdayRevenue = revenueSplit(orgId, today.minusDays(1));
        List<MonthlyEarning> monthlyEarnings = monthlyEarnings(orgId, today);

        return new DashboardResponse(servicesInProgress, servicesUnpaid, todayRevenue, yesterdayRevenue, monthlyEarnings);
    }

    private RevenueSplit revenueSplit(UUID orgId, LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime toExclusive = day.plusDays(1).atStartOfDay();
        BigDecimal paid = sumNet(serviceOrders.findPaidRevenueLines(orgId, from, toExclusive));
        BigDecimal unpaid = sumNet(serviceOrders.findUnpaidRevenueLines(orgId, from, toExclusive));
        return new RevenueSplit(paid, unpaid);
    }

    /** Jan through the current month; later months are {@code null} regardless of any stray future-dated data. */
    private List<MonthlyEarning> monthlyEarnings(UUID orgId, LocalDate today) {
        LocalDate yearStart = today.withDayOfYear(1);
        List<ChartRevenueLine> lines = serviceOrders.findPaidRevenueLines(
                orgId, yearStart.atStartOfDay(), today.plusDays(1).atStartOfDay());

        Map<Integer, BigDecimal> byMonth = new HashMap<>();
        for (ChartRevenueLine line : lines) {
            byMonth.merge(line.orderCreatedAt().getMonthValue(), netAmount(line), BigDecimal::add);
        }

        int currentMonth = today.getMonthValue();
        List<MonthlyEarning> result = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            BigDecimal amount = month > currentMonth ? null : byMonth.getOrDefault(month, GstBreakdown.zero().net());
            result.add(new MonthlyEarning(month, amount));
        }
        return result;
    }

    private static BigDecimal sumNet(List<ChartRevenueLine> lines) {
        BigDecimal total = GstBreakdown.zero().net();
        for (ChartRevenueLine line : lines) {
            total = total.add(netAmount(line));
        }
        return total;
    }

    private static BigDecimal netAmount(ChartRevenueLine line) {
        return GstBreakdown.of(line.basePrice(), line.gstPercentage(), line.gstIncluded())
                .times(line.quantity())
                .net();
    }
}
