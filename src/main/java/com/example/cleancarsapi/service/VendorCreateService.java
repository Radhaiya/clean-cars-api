package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.VendorRequest;
import com.example.cleancarsapi.dto.VendorResponse;
import com.example.cleancarsapi.entity.Vendor;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CREATE half of the vendor CRUD. */
@Service
@RequiredArgsConstructor
public class VendorCreateService {

    private final VendorRepository vendors;

    @Transactional
    public VendorResponse create(long orgId, VendorRequest request) {
        Vendor vendor = new Vendor();
        vendor.setOrgId(orgId);
        request.applyTo(vendor);
        return VendorResponse.from(vendors.save(vendor));
    }
}
