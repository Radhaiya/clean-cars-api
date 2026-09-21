package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByIdAndOrgId(UUID id, UUID orgId);

    long countByOrgId(UUID orgId);

    List<Employee> findByOrgIdAndIdIn(UUID orgId, Collection<UUID> ids);

    @Query("""
            select e from Employee e
            where e.orgId = :orgId
              and (:search is null or lower(e.name) like lower(concat('%', :search, '%')))
            """)
    Page<Employee> search(@Param("orgId") UUID orgId, @Param("search") String search, Pageable pageable);
}
