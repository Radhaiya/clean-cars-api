package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link BikeFuelType} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class BikeFuelTypeConverter implements AttributeConverter<BikeFuelType, String> {

    @Override
    public String convertToDatabaseColumn(BikeFuelType attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public BikeFuelType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BikeFuelType.fromDb(dbData);
    }
}
