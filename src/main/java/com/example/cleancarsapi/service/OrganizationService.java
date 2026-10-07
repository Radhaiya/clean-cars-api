package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.OrganizationCountryCodeRequest;
import com.example.cleancarsapi.dto.OrganizationInvoiceSettingsRequest;
import com.example.cleancarsapi.dto.OrganizationUpdateRequest;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public Organization getById(UUID id) {
        return organizations.findById(id)
                .orElseThrow(() -> new NotFoundException("organization", id));
    }

    /** Set just the phone country (owner only) — the one-tap path for orgs that predate the field. */
    @Transactional
    public Organization updateCountryCode(UUID id, OrganizationCountryCodeRequest request) {
        AuthContext.require(UserRole.OWNER);
        var resolved = ReferenceDataService.requireCountryCode(request.countryCode());
        Organization org = getById(id);
        org.setPhoneCountryIso(resolved.isoCode());
        org.setPhoneDialCode(resolved.dialCode());
        return organizations.save(org);
    }

    /** Set just the invoice template + accent colour (owner only). */
    @Transactional
    public Organization updateInvoiceSettings(UUID id, OrganizationInvoiceSettingsRequest request) {
        AuthContext.require(UserRole.OWNER);
        Organization org = getById(id);
        org.setInvoiceTemplate(request.template());
        org.setInvoiceColor(request.color().toUpperCase());
        return organizations.save(org);
    }

    /** Full-replace update of the caller's own org (owner only); null body fields clear stored values. */
    @Transactional
    public Organization update(UUID id, OrganizationUpdateRequest request) {
        AuthContext.require(UserRole.OWNER);
        Organization org = getById(id);
        request.applyTo(org);
        return organizations.save(org);
    }
}
