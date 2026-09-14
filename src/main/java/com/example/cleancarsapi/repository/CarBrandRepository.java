package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.CarBrand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CarBrandRepository extends JpaRepository<CarBrand, Long> {

    Optional<CarBrand> findByIdAndOrgId(Long id, Long orgId);

    List<CarBrand> findByOrgIdOrderByNameAsc(long orgId);

    boolean existsByIdAndOrgId(long id, long orgId);

    boolean existsByOrgIdAndName(long orgId, String name);

    boolean existsByOrgIdAndNameAndIdNot(long orgId, String name, long id);

    @Query("""
            select b from CarBrand b
            where b.orgId = :orgId
              and (:search is null or lower(b.name) like lower(concat('%', :search, '%')))
            """)
    Page<CarBrand> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
