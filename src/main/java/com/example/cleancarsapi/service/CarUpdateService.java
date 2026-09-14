package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarRequest;
import com.example.cleancarsapi.dto.CarResponse;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UPDATE half of the car CRUD. */
@Service
@RequiredArgsConstructor
public class CarUpdateService {

    private final CarRepository cars;
    private final CarReferenceValidator references;

    @Transactional
    public CarResponse update(long orgId, long id, CarRequest request) {
        Car car = cars.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("car", id));

        references.validate(orgId, request);
        request.applyTo(car);
        return CarResponse.from(cars.save(car));
    }
}
