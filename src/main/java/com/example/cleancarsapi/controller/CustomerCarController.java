package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.CustomerCarsResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.CustomerCarService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only lookup: a page of customers (searchable by name), each with their
 * cars ({@code carId} + car number + brand/model names), for the service-order
 * create form.
 */
@RestController
@RequestMapping("/api/customer-cars")
@RequiredArgsConstructor
public class CustomerCarController {

    private final CustomerCarService customerCarService;

    @GetMapping
    public PageResponse<CustomerCarsResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return customerCarService.list(AuthContext.requireOrgId(), search, pageable);
    }
}
