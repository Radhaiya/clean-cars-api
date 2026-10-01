package com.example.cleancarsapi.repository;

import com.example.cleancarsapi.entity.AmcVariantRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AmcVariantRowRepository extends JpaRepository<AmcVariantRow, UUID> {

    List<AmcVariantRow> findByVariantIdIn(Collection<UUID> variantIds);

    void deleteByVariantId(UUID variantId);
}
