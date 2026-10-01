package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.FeatureProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeaturePropertyRepository extends JpaRepository<FeatureProperty, UUID> {

    Optional<FeatureProperty> findByKey(String key);

    List<FeatureProperty> findByOrderByKeyAsc();
}
