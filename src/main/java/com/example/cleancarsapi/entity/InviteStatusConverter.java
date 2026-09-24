package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link InviteStatus} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class InviteStatusConverter implements AttributeConverter<InviteStatus, String> {

    @Override
    public String convertToDatabaseColumn(InviteStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public InviteStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : InviteStatus.fromDb(dbData);
    }
}
