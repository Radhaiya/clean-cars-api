package com.example.cleancarsapi.service;

import com.example.cleancarsapi.entity.Bike;
import com.example.cleancarsapi.exception.NotFoundException;
import com.example.cleancarsapi.repository.BikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** DELETE half of the bike CRUD — a soft delete, so the bike's service orders keep resolving it. */
@Service
@RequiredArgsConstructor
public class BikeDeleteService {

    private final BikeRepository bikes;

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Bike bike = bikes.findByIdAndOrgIdAndDeletedFalse(id, orgId)
                .orElseThrow(() -> new NotFoundException("bike", id));
        bike.setDeleted(true);
        bikes.save(bike);
    }
}
