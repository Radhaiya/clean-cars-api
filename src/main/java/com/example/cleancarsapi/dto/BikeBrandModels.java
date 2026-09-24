package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.BikeModel;

import java.util.List;
import java.util.UUID;
/** One bike brand plus its models — a lookup shape for populating the "create bike" form. */
public record BikeBrandModels(UUID brandId, String brandName, List<Model> models) {

    public record Model(UUID id, String name) {
        public static Model from(BikeModel model) {
            return new Model(model.getId(), model.getName());
        }
    }
}
