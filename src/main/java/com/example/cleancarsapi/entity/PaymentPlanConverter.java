package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link PaymentPlan} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class PaymentPlanConverter implements AttributeConverter<PaymentPlan, String> {

    @Override
    public String convertToDatabaseColumn(PaymentPlan attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public PaymentPlan convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PaymentPlan.fromDb(dbData);
    }
}
