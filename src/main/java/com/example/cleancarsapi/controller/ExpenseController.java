package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.ExpenseRequest;
import com.example.cleancarsapi.dto.ExpenseResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.ExpenseCreateService;
import com.example.cleancarsapi.service.ExpenseDeleteService;
import com.example.cleancarsapi.service.ExpenseReadService;
import com.example.cleancarsapi.service.ExpenseUpdateService;
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

/**
 * CRUD for the caller's per-org expenses. Each operation delegates to its own
 * service — see docs/ARCHITECTURE.md. Only the unit amount and GST inputs are
 * stored; net/GST/gross (unit and line) are computed on read.
 */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseCreateService createService;
    private final ExpenseReadService readService;
    private final ExpenseUpdateService updateService;
    private final ExpenseDeleteService deleteService;

    @GetMapping
    public PageResponse<ExpenseResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return readService.list(AuthContext.requireOrgId(), search, pageable);
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable long id) {
        return readService.get(AuthContext.requireOrgId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) {
        return createService.create(AuthContext.requireOrgId(), request);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable long id, @Valid @RequestBody ExpenseRequest request) {
        return updateService.update(AuthContext.requireOrgId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        deleteService.delete(AuthContext.requireOrgId(), id);
    }
}
