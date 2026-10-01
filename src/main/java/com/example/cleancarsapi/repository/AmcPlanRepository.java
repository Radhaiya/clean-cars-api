package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AmcPlanRepository extends JpaRepository<AmcPlan, UUID> {

    Optional<AmcPlan> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByOrgIdAndName(UUID orgId, String name);

    boolean existsByOrgIdAndNameAndIdNot(UUID orgId, String name, UUID id);

    @Query("""
            select p from AmcPlan p
            where p.orgId = :orgId
              and (:includeArchived = true or p.archived = false)
              and (:search is null or lower(p.name) like lower(concat('%', :search, '%')))
            """)
    Page<AmcPlan> search(@Param("orgId") UUID orgId, @Param("search") String search,
                         @Param("includeArchived") boolean includeArchived, Pageable pageable);
}
