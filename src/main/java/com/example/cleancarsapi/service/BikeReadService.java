package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.BikeAndServicesResponse;
import com.example.cleancarsapi.dto.BikeResponse;
import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** READ half of the bike CRUD — single fetch (with the bike's service history) and paged listing. */
@Service
@RequiredArgsConstructor
public class BikeReadService {

    private final BikeRepository bikes;
    private final ServiceOrderAssembler serviceOrders;

    @Transactional(readOnly = true)
    public BikeAndServicesResponse get(UUID orgId, UUID id) {
        Bike bike = bikes.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new NotFoundException("bike", id));
        return BikeAndServicesResponse.of(bike, serviceOrders.historyForBike(orgId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BikeResponse> list(UUID orgId, UUID customerId, String search, Pageable pageable) {
        String term = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(bikes.search(orgId, customerId, term, pageable).map(BikeResponse::from));
    }
}
