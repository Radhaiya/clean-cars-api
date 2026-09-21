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
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByIdAndOrgId(UUID id, UUID orgId);

    List<Vendor> findByOrgIdAndIdIn(UUID orgId, Collection<UUID> ids);

    //TODO remove %s from beginning
    @Query("""
            select v from Vendor v
            where v.orgId = :orgId
              and (:search is null
                   or lower(v.name) like lower(concat('%', :search, '%'))
                   or lower(v.contactPhone) like lower(concat('%', :search, '%')))
            """)
    Page<Vendor> search(@Param("orgId") UUID orgId, @Param("search") String search, Pageable pageable);
}
