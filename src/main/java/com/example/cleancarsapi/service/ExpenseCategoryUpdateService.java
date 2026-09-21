package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseCategoryRequest;
import com.example.cleancarsapi.dto.ExpenseCategoryResponse;
import com.example.cleancarsapi.entity.ExpenseCategory;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** UPDATE half of the expense-category CRUD. */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryUpdateService {

    private final ExpenseCategoryRepository categories;

    @Transactional
    public ExpenseCategoryResponse update(UUID orgId, UUID id, ExpenseCategoryRequest request) {
        ExpenseCategory category = categories.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("category", id));

        String name = request.name().trim();
        if (!category.getName().equals(name) && categories.existsByOrgIdAndNameAndIdNot(orgId, name, id)) {
            throw ConflictException.expenseCategoryNameExists(name);
        }

        request.applyTo(category);
        return ExpenseCategoryResponse.from(categories.save(category));
    }
}
