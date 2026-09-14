package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link PaymentType} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class PaymentTypeConverter implements AttributeConverter<PaymentType, String> {

    @Override
    public String convertToDatabaseColumn(PaymentType attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public PaymentType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PaymentType.fromDb(dbData);
    }
}
