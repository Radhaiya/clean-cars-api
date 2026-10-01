package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** DELETE half of the car CRUD — a soft delete, so the car's service orders keep resolving it. */
@Service
@RequiredArgsConstructor
public class CarDeleteService {

    private final CarRepository cars;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Car car = cars.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("car", id));
        car.setDeleted(true);
        cars.save(car);
    }
}
