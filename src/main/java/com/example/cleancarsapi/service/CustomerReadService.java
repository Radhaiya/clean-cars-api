package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerAndCarsResponse;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** READ half of the customer CRUD — single fetch (with the customer's cars) and paged listing. */
@Service
@RequiredArgsConstructor
public class CustomerReadService {

    private final CustomerRepository customers;
    private final CarRepository cars;

    @Transactional(readOnly = true)
    public CustomerAndCarsResponse get(long orgId, long id) {
        Customer customer = customers.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("customer", id));
        return CustomerAndCarsResponse.of(customer, cars.findSummariesByCustomer(orgId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(long orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(customers.search(orgId, term, pageable).map(CustomerResponse::from));
    }
}
