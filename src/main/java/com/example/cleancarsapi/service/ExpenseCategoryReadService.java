package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseCategoryResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** READ half of the expense-category CRUD — single fetch and paged listing. */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryReadService {

    private final ExpenseCategoryRepository categories;

    @Transactional(readOnly = true)
    public ExpenseCategoryResponse get(long orgId, long id) {
        return categories.findByIdAndOrgId(id, orgId)
                .map(ExpenseCategoryResponse::from)
                .orElseThrow(() -> new NotFoundException("category", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseCategoryResponse> list(long orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(categories.search(orgId, term, pageable).map(ExpenseCategoryResponse::from));
    }
}
