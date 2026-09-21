package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.VendorRequest;
import com.example.cleancarsapi.dto.VendorResponse;
import com.example.cleancarsapi.entity.Vendor;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** UPDATE half of the vendor CRUD. */
@Service
@RequiredArgsConstructor
public class VendorUpdateService {

    private final VendorRepository vendors;

    @Transactional
    public VendorResponse update(UUID orgId, UUID id, VendorRequest request) {
        Vendor vendor = vendors.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("vendor", id));

        request.applyTo(vendor);
        return VendorResponse.from(vendors.save(vendor));
    }
}
