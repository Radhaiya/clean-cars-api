package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DELETE half of the employee CRUD. */
@Service
@RequiredArgsConstructor
public class EmployeeDeleteService {

    private final EmployeeRepository employees;

    @Transactional
    public void delete(long orgId, long id) {
        Employee employee = employees.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("employee", id));
        employees.delete(employee);
    }
}
