package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.dto.CustomerBikeSummary;
import com.example.cleancarsapi.entity.Bike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BikeRepository extends JpaRepository<Bike, UUID> {

    Optional<Bike> findByIdAndOrgId(UUID id, UUID orgId);

    /** Live (not soft-deleted) lookup — use {@link #findByIdAndOrgId} when a deleted row must still resolve. */
    Optional<Bike> findByIdAndOrgIdAndDeletedFalse(UUID id, UUID orgId);

    long countByOrgIdAndDeletedFalse(UUID orgId);

    List<Bike> findByOrgIdAndIdIn(UUID orgId, java.util.Collection<UUID> ids);

    /** Bikes owned by a customer with brand/model resolved to names (both may be null). */
    @Query("""
            select new com.example.cleancarsapi.dto.CustomerBikeSummary(b.id, b.bikeNumber, br.name, m.name)
            from Bike b
            left join BikeBrand br on br.id = b.brandId
            left join BikeModel m on m.id = b.modelId
            where b.orgId = :orgId and b.customerId = :customerId and b.deleted = false
            order by b.bikeNumber asc
            """)
    List<CustomerBikeSummary> findSummariesByCustomer(@Param("orgId") UUID orgId,
                                                     @Param("customerId") UUID customerId);

    @Query("""
            select b from Bike b
            where b.orgId = :orgId
              and b.deleted = false
              and (:customerId is null or b.customerId = :customerId)
              and (:search is null
                   or lower(b.bikeNumber) like lower(concat('%', :search, '%'))
                   or (:searchKey is not null
                       and upper(replace(replace(replace(b.bikeNumber, ' ', ''), '-', ''), '.', ''))
                           like concat('%', :searchKey, '%')))
            """)
    Page<Bike> search(@Param("orgId") UUID orgId,
                      @Param("customerId") UUID customerId,
                      @Param("search") String search,
                      @Param("searchKey") String searchKey,
                      Pageable pageable);

    /** A live bike whose number matches {@code key} (see {@code NormalizedKeys.vehicle}), other than {@code excludeId}. */
    @Query("""
            select count(b) > 0 from Bike b
            where b.orgId = :orgId and b.deleted = false
              and b.id <> :excludeId
              and upper(replace(replace(replace(b.bikeNumber, ' ', ''), '-', ''), '.', '')) = :key
            """)
    boolean existsByNumberKey(@Param("orgId") UUID orgId, @Param("key") String key,
                              @Param("excludeId") UUID excludeId);
}
