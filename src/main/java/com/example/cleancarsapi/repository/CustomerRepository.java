package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByIdAndOrgId(long id, long orgId);

    long countByOrgId(long orgId);

    long countByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            long orgId, LocalDateTime from, LocalDateTime toExclusive);

    List<Customer> findByOrgIdAndIdIn(long orgId, Collection<Long> ids);

    boolean existsByOrgIdAndPhone(long orgId, String phone);

    boolean existsByOrgIdAndPhoneAndIdNot(long orgId, String phone, long id);

    /** Org-scoped listing with an optional name/phone contains-search. */
    @Query("""
            select c from Customer c
            where c.orgId = :orgId
              and (:search is null
                   or lower(c.name) like lower(concat('%', :search, '%'))
                   or c.phone like concat('%', :search, '%'))
            """)
    Page<Customer> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
