package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    /** SELECT ... FOR UPDATE — serialises concurrent subscribe / change-plan for the org. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") UUID id);

    /** Cross-org list for the internal console — name/contact search, newest first. */
    @Query("""
            select o from Organization o
            where :search is null
               or lower(o.name) like lower(concat('%', :search, '%'))
               or lower(coalesce(o.contactEmail, '')) like lower(concat('%', :search, '%'))
               or lower(coalesce(o.contactPhone, '')) like lower(concat('%', :search, '%'))
            order by o.createdAt desc
            """)
    Page<Organization> searchAcrossOrgs(@Param("search") String search, Pageable pageable);
}
