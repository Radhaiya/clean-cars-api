package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link SubscriptionStatus} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class SubscriptionStatusConverter implements AttributeConverter<SubscriptionStatus, String> {

    @Override
    public String convertToDatabaseColumn(SubscriptionStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public SubscriptionStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SubscriptionStatus.fromDb(dbData);
    }
}
