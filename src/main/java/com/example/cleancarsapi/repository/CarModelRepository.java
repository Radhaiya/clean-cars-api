package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.CarModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CarModelRepository extends JpaRepository<CarModel, Long> {

    Optional<CarModel> findByIdAndOrgId(Long id, Long orgId);

    List<CarModel> findByOrgIdOrderByNameAsc(long orgId);

    boolean existsByIdAndOrgId(long id, long orgId);

    boolean existsByOrgIdAndBrandIdAndName(long orgId, long brandId, String name);

    boolean existsByOrgIdAndBrandIdAndNameAndIdNot(long orgId, long brandId, String name, long id);

    boolean existsByBrandId(long brandId);

    @Query("""
            select m from CarModel m
            where m.orgId = :orgId
              and (:brandId is null or m.brandId = :brandId)
              and (:search is null or lower(m.name) like lower(concat('%', :search, '%')))
            """)
    Page<CarModel> search(@Param("orgId") long orgId,
                          @Param("brandId") Long brandId,
                          @Param("search") String search,
                          Pageable pageable);
}
