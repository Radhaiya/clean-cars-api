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
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByIdAndOrgId(UUID id, UUID orgId);

    /** Live (not soft-deleted) lookup — use {@link #findByIdAndOrgId} when a deleted row must still resolve. */
    Optional<Customer> findByIdAndOrgIdAndDeletedFalse(UUID id, UUID orgId);

    boolean existsByIdAndOrgIdAndDeletedFalse(UUID id, UUID orgId);

    long countByOrgIdAndDeletedFalse(UUID orgId);

    long countByOrgIdAndDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID orgId, LocalDateTime from, LocalDateTime toExclusive);

    List<Customer> findByOrgIdAndIdIn(UUID orgId, Collection<UUID> ids);

    boolean existsByOrgIdAndPhone(UUID orgId, String phone);

    boolean existsByOrgIdAndPhoneAndIdNot(UUID orgId, String phone, UUID id);

    /** Org-scoped listing with an optional name/phone contains-search. */
    @Query("""
            select c from Customer c
            where c.orgId = :orgId
              and c.deleted = false
              and (:search is null
                   or lower(c.name) like lower(concat('%', :search, '%'))
                   or c.phone like concat('%', :search, '%'))
            """)
    Page<Customer> search(@Param("orgId") UUID orgId, @Param("search") String search, Pageable pageable);
}
