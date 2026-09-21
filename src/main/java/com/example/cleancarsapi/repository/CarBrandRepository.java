package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.CarBrand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarBrandRepository extends JpaRepository<CarBrand, UUID> {

    Optional<CarBrand> findByIdAndOrgId(UUID id, UUID orgId);

    List<CarBrand> findByOrgIdOrderByNameAsc(UUID orgId);

    boolean existsByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndName(UUID orgId, String name);

    boolean existsByOrgIdAndNameAndIdNot(UUID orgId, String name, UUID id);

    @Query("""
            select b from CarBrand b
            where b.orgId = :orgId
              and (:search is null or lower(b.name) like lower(concat('%', :search, '%')))
            """)
    Page<CarBrand> search(@Param("orgId") UUID orgId, @Param("search") String search, Pageable pageable);
}
