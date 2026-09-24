package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ServiceOrderRequest;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.EmployeeRepository;
import com.example.cleancarsapi.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Checks that a service order's vehicle / employee / vendor all belong to the caller's org. */
@Component
@RequiredArgsConstructor
public class ServiceOrderReferenceValidator {

    private final CarRepository cars;
    private final BikeRepository bikes;
    private final EmployeeRepository employees;
    private final VendorRepository vendors;

    /**
     * Resolves the order's vehicle (exactly one of {@code carId} / {@code bikeId} —
     * the vehicle's owner becomes the order's customer) and validates the assignments.
     */
    public UUID resolveVehicle(UUID orgId, ServiceOrderRequest request) {
        if (request.carId() != null && request.bikeId() != null) {
            throw new BadRequestException("An order belongs to one vehicle — set either carId or bikeId, not both");
        }
        if (request.carId() != null) {
            Car car = cars.findByIdAndOrgId(request.carId(), orgId)
                    .orElseThrow(() -> new NotFoundException("car", request.carId()));
            validateAssignments(orgId, request);
            return car.getCustomerId();
        }
        if (request.bikeId() != null) {
            Bike bike = bikes.findByIdAndOrgId(request.bikeId(), orgId)
                    .orElseThrow(() -> new NotFoundException("bike", request.bikeId()));
            validateAssignments(orgId, request);
            return bike.getCustomerId();
        }
        throw new BadRequestException("Either carId or bikeId is required");
    }

    /** Validates only the employee and vendor refs — used on update, where the vehicle is fixed. */
    public void validateAssignments(UUID orgId, ServiceOrderRequest request) {
        if (request.employeeId() != null && !employees.existsByIdAndOrgId(request.employeeId(), orgId)) {
            throw new NotFoundException("employee", request.employeeId());
        }
        if (request.vendorId() != null && !vendors.existsByIdAndOrgId(request.vendorId(), orgId)) {
            throw new NotFoundException("vendor", request.vendorId());
        }
    }
}
