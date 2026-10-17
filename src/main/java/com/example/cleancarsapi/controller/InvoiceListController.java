package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.InvoiceListItem;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.InvoiceListService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Every generated invoice of the caller's org (service-order and AMC), searchable, filterable and paged. */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceListController {

    private final InvoiceListService listService;

    @GetMapping
    public PageResponse<InvoiceListItem> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listService.list(AuthContext.requireOrgId(), search, type, from, to, sort,
                "asc".equalsIgnoreCase(dir), page, size);
    }
}
