package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.BikeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BikeModelRepository extends JpaRepository<BikeModel, UUID> {

    Optional<BikeModel> findByIdAndOrgId(UUID id, UUID orgId);

    List<BikeModel> findByOrgIdOrderByNameAsc(UUID orgId);

    boolean existsByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndBrandIdAndName(UUID orgId, UUID brandId, String name);

    boolean existsByOrgIdAndBrandIdAndNameAndIdNot(UUID orgId, UUID brandId, String name, UUID id);

    boolean existsByBrandId(UUID brandId);

    @Query("""
            select m from BikeModel m
            where m.orgId = :orgId
              and (:brandId is null or m.brandId = :brandId)
              and (:search is null or lower(m.name) like lower(concat('%', :search, '%')))
            """)
    Page<BikeModel> search(@Param("orgId") UUID orgId,
                           @Param("brandId") UUID brandId,
                           @Param("search") String search,
                           Pageable pageable);
}
