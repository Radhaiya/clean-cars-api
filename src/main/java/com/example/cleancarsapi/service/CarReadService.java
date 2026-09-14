package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.CarAndServicesResponse;
import com.example.cleancarsapi.dto.CarResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.Car;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** READ half of the car CRUD — single fetch (with the car's service history) and paged listing. */
@Service
@RequiredArgsConstructor
public class CarReadService {

    private final CarRepository cars;
    private final ServiceOrderAssembler serviceOrders;

    @Transactional(readOnly = true)
    public CarAndServicesResponse get(long orgId, long id) {
        Car car = cars.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("car", id));
        return CarAndServicesResponse.of(car, serviceOrders.historyForCar(orgId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CarResponse> list(long orgId, Long customerId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(cars.search(orgId, customerId, term, pageable).map(CarResponse::from));
    }
}
