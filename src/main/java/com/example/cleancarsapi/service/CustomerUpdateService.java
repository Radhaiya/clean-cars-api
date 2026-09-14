package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerRequest;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UPDATE half of the customer CRUD. */
@Service
@RequiredArgsConstructor
public class CustomerUpdateService {

    private final CustomerRepository customers;

    @Transactional
    public CustomerResponse update(long orgId, long id, CustomerRequest request) {
        Customer customer = customers.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("customer", id));

        if (!customer.getPhone().equals(request.phone())
                && customers.existsByOrgIdAndPhoneAndIdNot(orgId, request.phone(), id)) {
            throw ConflictException.customerPhoneExists(request.phone());
        }

        request.applyTo(customer);
        return CustomerResponse.from(customers.save(customer));
    }
}
