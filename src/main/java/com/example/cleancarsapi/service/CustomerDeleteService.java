package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DELETE half of the customer CRUD. */
@Service
@RequiredArgsConstructor
public class CustomerDeleteService {

    private final CustomerRepository customers;

    @Transactional
    public void delete(long orgId, long id) {
        Customer customer = customers.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("customer", id));
        customers.delete(customer);
    }
}
