package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.BikeBrandModels;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.BikeBrandModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
/**
 * Read-only lookup for the "create bike" form: the caller's bike brands, each with
 * its models nested underneath.
 */
@RestController
@RequestMapping("/api/bike-brand-models")
@RequiredArgsConstructor
public class BikeBrandModelController {

    private final BikeBrandModelService bikeBrandModelService;

    @GetMapping
    public List<BikeBrandModels> list() {
        return bikeBrandModelService.listByBrand(AuthContext.requireOrgId());
    }
}
