package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.ExpenseCategory;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared: validate an expense's optional {@code categoryId} against the caller's org. */
@Component
@RequiredArgsConstructor
public class ExpenseCategoryLookup {

    private final ExpenseCategoryRepository categories;

    /**
     * Null {@code categoryId} → null. Otherwise the category must belong to {@code orgId}
     * (404 if not) and its name is returned for the response.
     */
    public String requireNameInOrg(long orgId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findByIdAndOrgId(categoryId, orgId)
                .map(ExpenseCategory::getName)
                .orElseThrow(() -> new NotFoundException("category", categoryId));
    }
}
