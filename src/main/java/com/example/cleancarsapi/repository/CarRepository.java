package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.dto.CustomerCarSummary;
import com.example.cleancarsapi.entity.Car;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {

    Optional<Car> findByIdAndOrgId(UUID id, UUID orgId);

    long countByOrgId(UUID orgId);

    long countByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID orgId, LocalDateTime from, LocalDateTime toExclusive);

    List<Car> findByOrgIdAndIdIn(UUID orgId, java.util.Collection<UUID> ids);

    /** Cars owned by a customer with brand/model resolved to names (both may be null). */
    @Query("""
            select new com.example.cleancarsapi.dto.CustomerCarSummary(c.id, c.carNumber, b.name, m.name)
            from Car c
            left join CarBrand b on b.id = c.brandId
            left join CarModel m on m.id = c.modelId
            where c.orgId = :orgId and c.customerId = :customerId
            order by c.carNumber asc
            """)
    List<CustomerCarSummary> findSummariesByCustomer(@Param("orgId") UUID orgId,
                                                     @Param("customerId") UUID customerId);

    @Query("""
            select c from Car c
            where c.orgId = :orgId
              and (:customerId is null or c.customerId = :customerId)
              and (:search is null or lower(c.carNumber) like lower(concat('%', :search, '%')))
            """)
    Page<Car> search(@Param("orgId") UUID orgId,
                     @Param("customerId") UUID customerId,
                     @Param("search") String search,
                     Pageable pageable);
}
