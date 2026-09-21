package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.ExpenseCategoryRequest;
import com.example.cleancarsapi.dto.ExpenseCategoryResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.ExpenseCategoryCreateService;
import com.example.cleancarsapi.service.ExpenseCategoryDeleteService;
import com.example.cleancarsapi.service.ExpenseCategoryReadService;
import com.example.cleancarsapi.service.ExpenseCategoryUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
/**
 * CRUD for the caller's per-org expense categories. Each operation delegates to its
 * own service — see docs/ARCHITECTURE.md.
 */
@RestController
@RequestMapping("/api/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryCreateService createService;
    private final ExpenseCategoryReadService readService;
    private final ExpenseCategoryUpdateService updateService;
    private final ExpenseCategoryDeleteService deleteService;

    @GetMapping
    public PageResponse<ExpenseCategoryResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, pageable);
    }

    @GetMapping("/{id}")
    public ExpenseCategoryResponse get(@PathVariable UUID id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseCategoryResponse create(@Valid @RequestBody ExpenseCategoryRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public ExpenseCategoryResponse update(@PathVariable UUID id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
