package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerRequest;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the customer CRUD. One service per operation — see docs/ARCHITECTURE.md. */
@Service
@RequiredArgsConstructor
public class CustomerCreateService {

    private final CustomerRepository customers;
    private final OrganizationRepository organizations;

    @Transactional
    public CustomerResponse create(UUID orgId, CustomerRequest request) {
        // Phones are shown with the org's country code as prefix — it must exist before any customer does.
        boolean hasCountryCode = organizations.findById(orgId)
                .map(org -> org.getPhoneDialCode() != null)
                .orElse(false);
        if (!hasCountryCode) {
            throw ConflictException.countryCodeRequired();
        }
        if (customers.existsByOrgIdAndPhone(orgId, request.phone())) {
            throw ConflictException.customerPhoneExists(request.phone());
        }

        Customer customer = new Customer();
        customer.setOrgId(orgId);
        request.applyTo(customer);
        return CustomerResponse.from(customers.save(customer));
    }
}
