package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.EmployeeResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** READ half of the employee CRUD — single fetch and paged listing. */
@Service
@RequiredArgsConstructor
public class EmployeeReadService {

    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public EmployeeResponse get(long orgId, long id) {
        return employees.findByIdAndOrgId(id, orgId)
                .map(EmployeeResponse::from)
                .orElseThrow(() -> new NotFoundException("employee", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(long orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(employees.search(orgId, term, pageable).map(EmployeeResponse::from));
    }
}
