package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.ExpenseCategory;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DELETE half of the expense-category CRUD. Expenses keep their row — the
 * {@code expenses.category_id} FK is {@code ON DELETE SET NULL}, so any expenses in
 * this category simply become uncategorized.
 */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryDeleteService {

    private final ExpenseCategoryRepository categories;

    @Transactional
    public void delete(long orgId, long id) {
        ExpenseCategory category = categories.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("category", id));
        categories.delete(category);
    }
}
