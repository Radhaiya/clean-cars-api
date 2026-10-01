package com.example.cleancarsapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code feature_properties} row — a runtime switch edited from the internal
 * console. The value is a JSON document (boolean, string, number or object)
 * held as its raw JSON text; {@code FeaturePropertyService} parses it.
 */
@Entity
@Table(name = "feature_properties")
@Getter
@NoArgsConstructor
public class FeatureProperty {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "property_key", nullable = false, updatable = false)
    private String key;

    @Setter
    @Column(name = "property_value", nullable = false, columnDefinition = "json")
    private String valueJson;

    @Setter
    private String description;

    @Setter
    private String updatedByEmail;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public FeatureProperty(String key, String valueJson) {
        this.key = key;
        this.valueJson = valueJson;
    }
}
