package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.CarModel;

import java.util.List;
import java.util.UUID;
/** One brand plus its models — a lookup shape for populating the "create car" form. */
public record BrandModels(UUID brandId, String brandName, List<Model> models) {

    public record Model(UUID id, String name) {
        public static Model from(CarModel model) {
            return new Model(model.getId(), model.getName());
        }
    }
}
