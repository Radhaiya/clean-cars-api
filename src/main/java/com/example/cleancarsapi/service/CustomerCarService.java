package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerCarsResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * Read-only lookup (single class, no CRUD split): a page of customers matched by
 * name, each with their cars, to populate the service-order create form.
 */
@Service
@RequiredArgsConstructor
public class CustomerCarService {

    private final CustomerRepository customers;
    private final CarRepository cars;

    @Transactional(readOnly = true)
    public PageResponse<CustomerCarsResponse> list(UUID orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(customers.search(orgId, term, pageable)
                .map(c -> CustomerCarsResponse.of(c, cars.findSummariesByCustomer(orgId, c.getId()))));
    }
}
