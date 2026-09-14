package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link FuelType} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class FuelTypeConverter implements AttributeConverter<FuelType, String> {

    @Override
    public String convertToDatabaseColumn(FuelType attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public FuelType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FuelType.fromDb(dbData);
    }
}
