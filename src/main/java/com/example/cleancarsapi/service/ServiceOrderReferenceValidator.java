package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Checks that a service order's car / employee / vendor all belong to the caller's org. */
@Component
@RequiredArgsConstructor
public class ServiceOrderReferenceValidator {

    private final CarRepository cars;
    private final EmployeeRepository employees;
    private final VendorRepository vendors;

    /** Resolves the car (its owner becomes the order's customer) and validates the assignments. */
    public Car resolveCar(long orgId, ServiceOrderRequest request) {
        Car car = cars.findByIdAndOrgId(request.carId(), orgId)
                .orElseThrow(() -> new NotFoundException("car", request.carId()));
        validateAssignments(orgId, request);
        return car;
    }

    /** Validates only the employee and vendor refs — used on update, where the car is fixed. */
    public void validateAssignments(long orgId, ServiceOrderRequest request) {
        if (request.employeeId() != null && !employees.existsByIdAndOrgId(request.employeeId(), orgId)) {
            throw new NotFoundException("employee", request.employeeId());
        }
        if (request.vendorId() != null && !vendors.existsByIdAndOrgId(request.vendorId(), orgId)) {
            throw new NotFoundException("vendor", request.vendorId());
        }
    }
}
