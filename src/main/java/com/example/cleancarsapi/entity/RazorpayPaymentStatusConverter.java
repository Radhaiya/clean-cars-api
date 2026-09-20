package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link RazorpayPaymentStatus} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class RazorpayPaymentStatusConverter implements AttributeConverter<RazorpayPaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(RazorpayPaymentStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public RazorpayPaymentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RazorpayPaymentStatus.fromDb(dbData);
    }
}
