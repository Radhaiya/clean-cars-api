package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseRequest;
import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * UPDATE half of the expense CRUD — edit one row by id. A wrong-org or missing
 * id is a 404, indistinguishable from "doesn't exist". The new category name is
 * validated against the org's labels just like on create.
 */
@Service
@RequiredArgsConstructor
public class ExpenseUpdateService {

    private final ExpenseRepository expenses;
    private final ExpenseCreateService createService;

    @Transactional
    public ExpenseResponse update(UUID orgId, UUID id, ExpenseRequest request) {
        Expense expense = expenses.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("expense", id));
        createService.requireCategoryInOrg(orgId, request.categoryName());
        request.applyTo(expense);
        return ExpenseResponse.from(expenses.save(expense));
    }
}
