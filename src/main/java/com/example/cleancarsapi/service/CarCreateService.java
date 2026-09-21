package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarRequest;
import com.example.cleancarsapi.dto.CarResponse;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** CREATE half of the car CRUD. */
@Service
@RequiredArgsConstructor
public class CarCreateService {

    private final CarRepository cars;
    private final CarReferenceValidator references;

    @Transactional
    public CarResponse create(UUID orgId, CarRequest request) {
        references.validate(orgId, request);

        Car car = new Car();
        car.setOrgId(orgId);
        request.applyTo(car);
        return CarResponse.from(cars.save(car));
    }
}
