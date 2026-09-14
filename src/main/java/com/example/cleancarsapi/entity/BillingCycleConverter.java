package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link BillingCycle} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class BillingCycleConverter implements AttributeConverter<BillingCycle, String> {

    @Override
    public String convertToDatabaseColumn(BillingCycle attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public BillingCycle convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BillingCycle.fromDb(dbData);
    }
}
