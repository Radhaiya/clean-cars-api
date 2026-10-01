package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the customer CRUD — a soft delete. The row stays (its cars, bikes and
 * service orders keep pointing at it) but only the name survives; the phone is freed.
 */
@Service
@RequiredArgsConstructor
public class CustomerDeleteService {

    private final CustomerRepository customers;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Customer customer = customers.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("customer", id));
        customer.softDelete();
        customers.save(customer);
    }
}
