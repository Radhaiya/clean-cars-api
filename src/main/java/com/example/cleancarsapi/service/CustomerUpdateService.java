package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerRequest;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CustomerRepository;
import com.example.cleancarsapi.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** UPDATE half of the customer CRUD. */
@Service
@RequiredArgsConstructor
public class CustomerUpdateService {

    private final CustomerRepository customers;
    private final OrganizationRepository organizations;

    @Transactional
    public CustomerResponse update(UUID orgId, UUID id, CustomerRequest request) {
        Customer customer = customers.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("customer", id));

        if (!customer.getPhone().equals(request.phone())) {
            organizations.findById(orgId).filter(o -> o.getPhoneDialCode() != null).ifPresent(org -> {
                String phoneError = CountryDialCodes.phoneLengthError(
                        org.getPhoneCountryIso(), org.getPhoneDialCode(), request.phone());
                if (phoneError != null) {
                    throw new BadRequestException(phoneError);
                }
            });
        }
        if (!customer.getPhone().equals(request.phone())
                && customers.existsByOrgIdAndPhoneAndIdNot(orgId, request.phone(), id)) {
            throw ConflictException.customerPhoneExists(request.phone());
        }

        request.applyTo(customer);
        return CustomerResponse.from(customers.save(customer));
    }
}
