package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerRequest;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the customer CRUD. One service per operation — see docs/ARCHITECTURE.md. */
@Service
@RequiredArgsConstructor
public class CustomerCreateService {

    private final CustomerRepository customers;

    @Transactional
    public CustomerResponse create(UUID orgId, CustomerRequest request) {
        if (customers.existsByOrgIdAndPhone(orgId, request.phone())) {
            throw ConflictException.customerPhoneExists(request.phone());
        }

        Customer customer = new Customer();
        customer.setOrgId(orgId);
        request.applyTo(customer);
        return CustomerResponse.from(customers.save(customer));
    }
}
