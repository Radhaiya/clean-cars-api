package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.EmployeeRequest;
import com.example.cleancarsapi.dto.EmployeeResponse;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UPDATE half of the employee CRUD. */
@Service
@RequiredArgsConstructor
public class EmployeeUpdateService {

    private final EmployeeRepository employees;

    @Transactional
    public EmployeeResponse update(long orgId, long id, EmployeeRequest request) {
        Employee employee = employees.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("employee", id));

        request.applyTo(employee);
        return EmployeeResponse.from(employees.save(employee));
    }
}
