package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * READ half of the expense CRUD — the flat newest-first listing, search
 * matched against the denormalized category name and notes.
 */
@Component
@RequiredArgsConstructor
public class ExpenseReadService {

    private final ExpenseRepository expenses;

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(long orgId, String search) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return expenses.search(orgId, term).stream()
                .map(ExpenseResponse::from)
                .toList();
    }
}
