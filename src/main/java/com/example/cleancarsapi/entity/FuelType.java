package com.example.cleancarsapi.entity;

/** Fuel types, persisted lowercase in the {@code cars.fuel_type} ENUM via {@link FuelTypeConverter}. */
public enum FuelType {
    PETROL,
    DIESEL,
    ELECTRIC,
    HYBRID,
    CNG,
    LPG;

    public static FuelType fromDb(String value) {
        return FuelType.valueOf(value.trim().toUpperCase());
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
