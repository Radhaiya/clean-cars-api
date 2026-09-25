package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.EmployeeRequest;
import com.example.cleancarsapi.dto.EmployeeResponse;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.service.internal.PlanLimitService;
import com.example.cleancarsapi.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * CREATE half of the employee CRUD. The employee roster is the plan seat
 * (docs/FEATURE-INVITES.md): {@code POST /api/employees} is where the plan's
 * {@code maxUsers} "n workers" limit actually bites — a full roster 409s on
 * create, not on invite.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeCreateService {

    private final EmployeeRepository employees;
    private final PlanLimitService planLimits;

    @Transactional
    public EmployeeResponse create(UUID orgId, EmployeeRequest request) {
        planLimits.assertCanAddUser(orgId);
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase();
            if (employees.findByOrgIdAndEmailIgnoreCase(orgId, email).isPresent()) {
                throw ConflictException.employeeEmailExists(email);
            }
        }

        Employee employee = new Employee();
        employee.setOrgId(orgId);
        request.applyTo(employee);
        log.info("Employee created: org={} id={} name={}", orgId, employee.getId(), employee.getName());
        return EmployeeResponse.from(employees.save(employee));
    }
}
