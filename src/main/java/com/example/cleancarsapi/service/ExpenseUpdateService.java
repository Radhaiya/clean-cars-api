package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseRequest;
import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UPDATE half of the expense CRUD. */
@Service
@RequiredArgsConstructor
public class ExpenseUpdateService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryLookup categoryLookup;

    @Transactional
    public ExpenseResponse update(long orgId, long id, ExpenseRequest request) {
        Expense expense = expenses.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("expense", id));

        String categoryName = categoryLookup.requireNameInOrg(orgId, request.categoryId());

        request.applyTo(expense);
        return ExpenseResponse.from(expenses.save(expense), categoryName);
    }
}
