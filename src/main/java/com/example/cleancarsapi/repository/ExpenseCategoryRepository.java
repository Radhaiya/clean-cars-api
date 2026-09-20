package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.ExpenseCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    Optional<ExpenseCategory> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByIdAndOrgId(long id, long orgId);

    boolean existsByOrgIdAndName(long orgId, String name);

    boolean existsByOrgIdAndNameAndIdNot(long orgId, String name, long id);

    /** Expense create/update validates the denormalized name against the org's labels (case-insensitive). */
    Optional<ExpenseCategory> findByOrgIdAndNameIgnoreCase(long orgId, String name);

    @Query("""
            select c from ExpenseCategory c
            where c.orgId = :orgId
              and (:search is null or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<ExpenseCategory> search(@Param("orgId") long orgId, @Param("search") String search, Pageable pageable);
}
