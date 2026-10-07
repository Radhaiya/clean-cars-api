package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CustomerRequest;
import com.example.cleancarsapi.dto.CustomerResponse;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.BadRequestException;
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
        var org = organizations.findById(orgId)
                .filter(o -> o.getPhoneDialCode() != null)
                .orElseThrow(ConflictException::countryCodeRequired);
        String phoneError = CountryDialCodes.phoneLengthError(
                org.getPhoneCountryIso(), org.getPhoneDialCode(), request.phone());
        if (phoneError != null) {
            throw new BadRequestException(phoneError);
        }
        if (customers.existsByOrgIdAndPhone(orgId, request.phone())
                || customers.existsByPhoneDigits(orgId, NormalizedKeys.phone(request.phone()),
                        new java.util.UUID(0L, 0L))) {
            throw ConflictException.customerPhoneExists(request.phone());
        }

        Customer customer = new Customer();
        customer.setOrgId(orgId);
        request.applyTo(customer);
        return CustomerResponse.from(customers.save(customer));
    }
}
