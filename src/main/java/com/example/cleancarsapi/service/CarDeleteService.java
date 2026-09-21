package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/**
 * DELETE half of the car CRUD. A car still referenced by a service order hits the
 * DB foreign key and surfaces as 409 via {@code ApiExceptionHandler}.
 */
@Service
@RequiredArgsConstructor
public class CarDeleteService {

    private final CarRepository cars;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Car car = cars.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("car", id));
        cars.delete(car);
    }
}
