package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.EmployeeResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * READ half of the employee CRUD — single fetch and paged listing. Each row
 * carries the linked account (null until an invited person accepted), so the
 * owner's "Users" screen is one call.
 */
@Service
@RequiredArgsConstructor
public class EmployeeReadService {

    private final EmployeeRepository employees;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID orgId, UUID id) {
        return employees.findByIdAndOrgId(id, orgId)
                .map(employee -> EmployeeResponse.from(employee, membersById(orgId).get(employee.getUserId())))
                .orElseThrow(() -> new NotFoundException("employee", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(UUID orgId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        Map<UUID, User> members = membersById(orgId);
        Page<Employee> page = employees.search(orgId, term, pageable);
        return PageResponse.of(page.map(employee ->
                EmployeeResponse.from(employee, members.get(employee.getUserId()))));
    }

    /** The org's sign-in accounts keyed by {@code users.id} — what an employee row links to. */
    private Map<UUID, User> membersById(UUID orgId) {
        return users.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
