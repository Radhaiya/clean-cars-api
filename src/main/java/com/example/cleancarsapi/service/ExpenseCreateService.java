package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseRequest;
import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CREATE half of the expense CRUD. */
@Service
@RequiredArgsConstructor
public class ExpenseCreateService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryLookup categoryLookup;

    @Transactional
    public ExpenseResponse create(long orgId, ExpenseRequest request) {
        String categoryName = categoryLookup.requireNameInOrg(orgId, request.categoryId());

        Expense expense = new Expense();
        expense.setOrgId(orgId);
        request.applyTo(expense);
        return ExpenseResponse.from(expenses.save(expense), categoryName);
    }
}
