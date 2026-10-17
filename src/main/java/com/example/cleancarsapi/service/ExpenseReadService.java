package com.example.cleancarsapi.service;

import com.example.cleancarsapi.config.OrgTimeZoneResolver;
import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.dto.ExpenseStatsResponse;
import com.example.cleancarsapi.dto.TaxBreakdown;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
/**
 * READ half of the expense CRUD — the flat newest-first listing, search
 * matched against the denormalized category name and notes.
 */
@Component
@RequiredArgsConstructor
public class ExpenseReadService {

    private final ExpenseRepository expenses;
    private final OrgTimeZoneResolver orgTimezones;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(UUID orgId, String search) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return expenses.search(orgId, term).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    /** One expense with its category roll-up ({@code stats}). */
    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID orgId, UUID id) {
        Expense expense = expenses.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("expense", id));
        return ExpenseResponse.from(expense).withStats(stats(orgId, expense));
    }

    private ExpenseStatsResponse stats(UUID orgId, Expense expense) {
        YearMonth thisMonth = YearMonth.from(orgTimezones.now());
        BigDecimal category = BigDecimal.ZERO;
        BigDecimal categoryMonth = BigDecimal.ZERO;
        long count = 0;
        LocalDate first = null;
        LocalDate last = null;
        for (Expense e : expenses.findByOrgId(orgId)) {
            BigDecimal gross = gross(e);
            if (!e.getCategoryName().equalsIgnoreCase(expense.getCategoryName())) {
                continue;
            }
            count++;
            category = category.add(gross);
            LocalDate day = e.getCreatedAt().toLocalDate();
            if (YearMonth.from(day).equals(thisMonth)) {
                categoryMonth = categoryMonth.add(gross);
            }
            first = first == null || day.isBefore(first) ? day : first;
            last = last == null || day.isAfter(last) ? day : last;
        }
        return new ExpenseStatsResponse(count, category, categoryMonth,
                count == 0 ? BigDecimal.ZERO : category.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP),
                first, last);
    }

    private static BigDecimal gross(Expense e) {
        return TaxBreakdown.of(e.getAmount(), e.getTaxPercentage(), e.isTaxIncluded()).times(e.getQuantity()).gross();
    }
}
