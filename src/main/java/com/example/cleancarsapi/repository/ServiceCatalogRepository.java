package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.ServiceCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, Long> {

    Optional<ServiceCatalog> findByIdAndOrgId(Long id, Long orgId);

    long countByOrgId(long orgId);

    boolean existsByOrgIdAndName(long orgId, String name);

    boolean existsByOrgIdAndNameAndIdNot(long orgId, String name, long id);

    @Query("""
            select s from ServiceCatalog s
            left join ServiceCategory c on c.id = s.categoryId
            where s.orgId = :orgId
              and (:search is null
                   or lower(s.name) like lower(concat('%', :search, '%'))
                   or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<ServiceCatalog> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
