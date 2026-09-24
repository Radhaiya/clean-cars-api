package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code bikes} row. Only {@code customerId} and {@code bikeNumber} are required;
 * brand, model and the rest are optional. {@code bikeNumber} is intentionally
 * non-unique (plates get reassigned).
 */
@Entity
@Table(name = "bikes")
@Getter
@Setter
@NoArgsConstructor
public class Bike {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orgId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String bikeNumber;

    private UUID brandId;

    private UUID modelId;

    private Integer year;

    private String color;

    private BikeFuelType fuelType;

    private String chassisNumber;

    private String engineNumber;

    @Column(columnDefinition = "text")
    private String comments;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
