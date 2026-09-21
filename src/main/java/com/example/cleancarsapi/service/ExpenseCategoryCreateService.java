package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseCategoryRequest;
import com.example.cleancarsapi.dto.ExpenseCategoryResponse;
import com.example.cleancarsapi.entity.ExpenseCategory;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the expense-category CRUD. */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryCreateService {

    private final ExpenseCategoryRepository categories;

    @Transactional
    public ExpenseCategoryResponse create(UUID orgId, ExpenseCategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByOrgIdAndName(orgId, name)) {
            throw ConflictException.expenseCategoryNameExists(name);
        }

        ExpenseCategory category = new ExpenseCategory();
        category.setOrgId(orgId);
        request.applyTo(category);
        return ExpenseCategoryResponse.from(categories.save(category));
    }
}
