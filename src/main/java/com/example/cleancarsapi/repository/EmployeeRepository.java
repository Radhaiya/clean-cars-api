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

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByIdAndOrgId(long id, long orgId);

    long countByOrgId(long orgId);

    List<Employee> findByOrgIdAndIdIn(long orgId, Collection<Long> ids);

    @Query("""
            select e from Employee e
            where e.orgId = :orgId
              and (:search is null or lower(e.name) like lower(concat('%', :search, '%')))
            """)
    Page<Employee> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
