package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Vendor;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the vendor CRUD. A hard delete — a vendor still referenced by a
 * service order is protected by the DB FK, which {@code ApiExceptionHandler} maps to 409.
 */
@Service
@RequiredArgsConstructor
public class VendorDeleteService {

    private final VendorRepository vendors;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Vendor vendor = vendors.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("vendor", id));
        vendors.delete(vendor);
    }
}
