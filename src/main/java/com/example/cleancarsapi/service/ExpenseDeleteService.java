package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Expense;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the expense CRUD — one expense row by id (404 when
 * missing/wrong-org). Since {@code categoryName} is a denormalized string with
 * no FK, the category itself is never touched here.
 */
@Service
@RequiredArgsConstructor
public class ExpenseDeleteService {

    private final ExpenseRepository expenses;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Expense expense = expenses.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("expense", id));
        expenses.delete(expense);
    }
}
