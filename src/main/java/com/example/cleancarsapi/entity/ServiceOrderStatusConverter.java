package com.example.cleancarsapi.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link ServiceOrderStatus} to/from the lowercase strings the DB ENUM stores. */
@Converter(autoApply = true)
public class ServiceOrderStatusConverter implements AttributeConverter<ServiceOrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(ServiceOrderStatus attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public ServiceOrderStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ServiceOrderStatus.fromDb(dbData);
    }
}
