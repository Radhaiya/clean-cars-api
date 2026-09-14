package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndOrgId(Long id, Long orgId);

    /** For the KPI tiles' totalExpenses: every expense in range, net/GST/gross computed in code. */
    List<Expense> findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            long orgId, LocalDateTime from, LocalDateTime toExclusive);

    @Query("""
            select e from Expense e
            left join ExpenseCategory c on c.id = e.categoryId
            where e.orgId = :orgId
              and (:search is null
                   or lower(e.title) like lower(concat('%', :search, '%'))
                   or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<Expense> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
