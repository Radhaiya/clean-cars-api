package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.CustomerBikesResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.CustomerBikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
/**
 * Read-only lookup: a page of customers (searchable by name), each with their
 * bikes ({@code bikeId} + bike number + brand/model names), for the service-order
 * create form.
 */
@RestController
@RequestMapping("/api/customer-bikes")
@RequiredArgsConstructor
public class CustomerBikeController {

    private final CustomerBikeService customerBikeService;

    @GetMapping
    public PageResponse<CustomerBikesResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return customerBikeService.list(AuthContext.requireOrgId(), search, pageable);
    }
}
