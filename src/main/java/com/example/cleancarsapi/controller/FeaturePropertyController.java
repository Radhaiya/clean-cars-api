package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.service.FeaturePropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Public (permit-all) read of the {@code Client.*} runtime switches — the UI needs
 * them before anyone is signed in (maintenance page, login button state).
 */
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class FeaturePropertyController {

    private final FeaturePropertyService featurePropertyService;

    /** {@code {"Client.Maintenance.Mode.Enable": false, ...}} */
    @GetMapping
    public Map<String, JsonNode> list() {
        return featurePropertyService.publicProperties();
    }
}
