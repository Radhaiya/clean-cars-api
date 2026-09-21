package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.EmployeeRequest;
import com.example.cleancarsapi.dto.EmployeeResponse;
import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the employee CRUD. */
@Service
@RequiredArgsConstructor
public class EmployeeCreateService {

    private final EmployeeRepository employees;

    @Transactional
    public EmployeeResponse create(UUID orgId, EmployeeRequest request) {
        Employee employee = new Employee();
        employee.setOrgId(orgId);
        request.applyTo(employee);
        return EmployeeResponse.from(employees.save(employee));
    }
}
