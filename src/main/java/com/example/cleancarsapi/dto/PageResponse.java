package com.example.cleancarsapi.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable pagination envelope — avoids serializing Spring Data's {@code Page} directly. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
