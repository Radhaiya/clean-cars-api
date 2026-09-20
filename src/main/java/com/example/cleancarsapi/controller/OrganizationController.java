package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.OrganizationUpdateRequest;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /** The caller's own organization — org id comes straight from the token. */
    @GetMapping
    public Organization current() {
        return organizationService.getById(AuthContext.requireOrgId());
    }

    /** Edit the caller's own organization (owner/admin). Full replace — omitted fields clear. */
    @PutMapping
    public Organization update(@Valid @RequestBody OrganizationUpdateRequest request) {
        return organizationService.update(AuthContext.requireOrgId(), request);
    }


}
