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

    long countByOrgId(UUID orgId);

    List<Bike> findByOrgIdAndIdIn(UUID orgId, java.util.Collection<UUID> ids);

    /** Bikes owned by a customer with brand/model resolved to names (both may be null). */
    @Query("""
            select new com.example.cleancarsapi.dto.CustomerBikeSummary(b.id, b.bikeNumber, br.name, m.name)
            from Bike b
            left join BikeBrand br on br.id = b.brandId
            left join BikeModel m on m.id = b.modelId
            where b.orgId = :orgId and b.customerId = :customerId
            order by b.bikeNumber asc
            """)
    List<CustomerBikeSummary> findSummariesByCustomer(@Param("orgId") UUID orgId,
                                                     @Param("customerId") UUID customerId);

    @Query("""
            select b from Bike b
            where b.orgId = :orgId
              and (:customerId is null or b.customerId = :customerId)
              and (:search is null or lower(b.bikeNumber) like lower(concat('%', :search, '%')))
            """)
    Page<Bike> search(@Param("orgId") UUID orgId,
                      @Param("customerId") UUID customerId,
                      @Param("search") String search,
                      Pageable pageable);
}
