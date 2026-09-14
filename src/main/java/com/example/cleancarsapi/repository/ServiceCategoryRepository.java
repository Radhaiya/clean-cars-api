package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.ServiceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    Optional<ServiceCategory> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByIdAndOrgId(long id, long orgId);

    boolean existsByOrgIdAndName(long orgId, String name);

    boolean existsByOrgIdAndNameAndIdNot(long orgId, String name, long id);

    List<ServiceCategory> findByOrgIdAndIdIn(long orgId, Collection<Long> ids);

    @Query("""
            select c from ServiceCategory c
            where c.orgId = :orgId
              and (:search is null or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<ServiceCategory> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
