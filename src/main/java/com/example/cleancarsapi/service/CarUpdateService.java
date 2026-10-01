package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarRequest;
import com.example.cleancarsapi.dto.CarResponse;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.entity.Customer;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import com.example.cleancarsapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** UPDATE half of the car CRUD. */
@Service
@RequiredArgsConstructor
public class CarUpdateService {

    private final CarRepository cars;
    private final CarReferenceValidator references;
    private final CustomerRepository customers;

    @Transactional
    public CarResponse update(UUID orgId, UUID id, CarRequest request) {
        Car car = cars.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("car", id));

        references.validate(orgId, request, car.getCustomerId());
        request.applyTo(car);
        return CarResponse.from(cars.save(car), isOwnerDeleted(orgId, car.getCustomerId()));
    }

    /**
     * Hands the car to another live customer — works for a live or a deleted current owner.
     * Past service orders keep the customer they were opened for; only new orders follow the new owner.
     */
    @Transactional
    public CarResponse transferOwner(UUID orgId, UUID id, UUID newCustomerId) {
        Car car = cars.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("car", id));
        if (car.getCustomerId().equals(newCustomerId)) {
            throw ConflictException.ownerUnchanged();
        }
        if (!customers.existsByIdAndOrgIdAndDeletedFalse(newCustomerId, orgId)) {
            throw new NotFoundException("customer", newCustomerId);
        }
        car.setCustomerId(newCustomerId);
        return CarResponse.from(cars.save(car), false);
    }

    private boolean isOwnerDeleted(UUID orgId, UUID customerId) {
        return customers.findByIdAndOrgId(customerId, orgId).map(Customer::isDeleted).orElse(false);
    }
}
