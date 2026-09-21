package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.ServiceCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, UUID> {

    Optional<ServiceCatalog> findByIdAndOrgId(UUID id, UUID orgId);

    long countByOrgId(UUID orgId);

    boolean existsByOrgIdAndName(UUID orgId, String name);

    boolean existsByOrgIdAndNameAndIdNot(UUID orgId, String name, UUID id);

    @Query("""
            select s from ServiceCatalog s
            left join ServiceCategory c on c.id = s.categoryId
            where s.orgId = :orgId
              and (:search is null
                   or lower(s.name) like lower(concat('%', :search, '%'))
                   or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<ServiceCatalog> search(@Param("orgId") UUID orgId, @Param("search") String search, Pageable pageable);
}
