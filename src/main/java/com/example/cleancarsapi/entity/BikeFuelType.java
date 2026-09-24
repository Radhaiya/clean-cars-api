package com.example.cleancarsapi.entity;

/** Fuel types, persisted lowercase in the {@code bikes.fuel_type} ENUM via {@link BikeFuelTypeConverter}. */
public enum BikeFuelType {
    PETROL,
    ELECTRIC,
    CNG,
    LPG;

    public static BikeFuelType fromDb(String value) {
        return BikeFuelType.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
