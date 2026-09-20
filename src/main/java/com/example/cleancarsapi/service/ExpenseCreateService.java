package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseRequest;
import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseCategoryRepository;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CREATE half of the expense CRUD. */
@Service
@RequiredArgsConstructor
public class ExpenseCreateService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryRepository categories;

    /**
     * One request = one {@code expenses} row. The category name must be one of
     * the org's {@code expense_categories} (404 if not) — the dropdown supplies
     * it, this only stops typos creating stray labels. The name is stored
     * denormalized with no FK, so later category deletions leave the row intact.
     */
    @Transactional
    public ExpenseResponse create(long orgId, ExpenseRequest request) {
        requireCategoryInOrg(orgId, request.categoryName());
        Expense expense = new Expense();
        expense.setOrgId(orgId);
        request.applyTo(expense);
        return ExpenseResponse.from(expenses.save(expense));
    }

    /** Shared by update: the trimmed name must exist as a label of this org (404 otherwise). */
    void requireCategoryInOrg(long orgId, String categoryName) {
        if (categories.findByOrgIdAndNameIgnoreCase(orgId, categoryName.trim()).isEmpty()) {
            throw new NotFoundException("expense category", categoryName.trim());
        }
    }
}
