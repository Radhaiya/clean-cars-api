package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public Organization getById(long id) {
        return organizations.findById(id)
                .orElseThrow(() -> new NotFoundException("organization", id));
    }
}
