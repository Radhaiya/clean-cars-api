package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.BrandModels;
import com.example.cleancarsapi.security.AuthContext;
import com.example.cleancarsapi.service.BrandModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
/**
 * Read-only lookup for the "create car" form: the caller's brands, each with its
 * models nested underneath.
 */
@RestController
@RequestMapping("/api/brand-models")
@RequiredArgsConstructor
public class BrandModelController {

    private final BrandModelService brandModelService;

    @GetMapping
    public List<BrandModels> list() {
        return brandModelService.listByBrand(AuthContext.requireOrgId());
    }
}
