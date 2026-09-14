package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.CarModel;

import java.util.List;

/** One brand plus its models — a lookup shape for populating the "create car" form. */
public record BrandModels(Long brandId, String brandName, List<Model> models) {

    public record Model(Long id, String name) {
        public static Model from(CarModel model) {
            return new Model(model.getId(), model.getName());
        }
    }
}
