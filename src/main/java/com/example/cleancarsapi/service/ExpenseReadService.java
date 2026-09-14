package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.entity.ExpenseCategory;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseCategoryRepository;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** READ half of the expense CRUD — single fetch and paged listing, with category names resolved. */
@Service
@RequiredArgsConstructor
public class ExpenseReadService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryRepository categories;

    @Transactional(readOnly = true)
    public ExpenseResponse get(long orgId, long id) {
        Expense expense = expenses.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("expense", id));
        String categoryName = expense.getCategoryId() == null ? null
                : categories.findByIdAndOrgId(expense.getCategoryId(), orgId)
                        .map(ExpenseCategory::getName).orElse(null);
        return ExpenseResponse.from(expense, categoryName);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(long orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Page<Expense> page = expenses.search(orgId, term, pageable);

        List<Long> categoryIds = page.getContent().stream()
                .map(Expense::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names = categoryIds.isEmpty() ? Map.of()
                : categories.findByOrgIdAndIdIn(orgId, categoryIds).stream()
                        .collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName));

        return PageResponse.of(page.map(e -> {
            String categoryName = e.getCategoryId() == null ? null : names.get(e.getCategoryId());
            return ExpenseResponse.from(e, categoryName);
        }));
    }
}
