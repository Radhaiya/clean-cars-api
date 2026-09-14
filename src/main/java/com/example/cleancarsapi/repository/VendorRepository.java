package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByIdAndOrgId(long id, long orgId);

    List<Vendor> findByOrgIdAndIdIn(long orgId, Collection<Long> ids);

    //TODO remove %s from beginning
    @Query("""
            select v from Vendor v
            where v.orgId = :orgId
              and (:search is null
                   or lower(v.name) like lower(concat('%', :search, '%'))
                   or lower(v.contactPhone) like lower(concat('%', :search, '%')))
            """)
    Page<Vendor> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
