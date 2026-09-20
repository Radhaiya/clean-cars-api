package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.OrganizationUpdateRequest;
import com.example.cleancarsapi.entity.Organization;
import com.example.cleancarsapi.entity.UserRole;
import com.example.cleancarsapi.exception.ForbiddenException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.OrganizationRepository;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.security.AuthenticatedUser;
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

    /** Full-replace update of the caller's own org (owner/admin only); null body fields clear stored values. */
    @Transactional
    public Organization update(long id, OrganizationUpdateRequest request) {
        AuthenticatedUser me = AuthContext.require();
        if (!me.hasRole(UserRole.OWNER) && !me.hasRole(UserRole.ADMIN)) {
            throw new ForbiddenException("Access denied: requires role owner or admin");
        }
        Organization org = getById(id);
        request.applyTo(org);
        return organizations.save(org);
    }
}
