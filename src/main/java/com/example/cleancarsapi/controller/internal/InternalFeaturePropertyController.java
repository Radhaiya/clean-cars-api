package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.internal.FeaturePropertyResponse;
import com.example.cleancarsapi.dto.internal.FeaturePropertyUpdateRequest;
import com.example.cleancarsapi.entity.FeatureProperty;
import com.example.cleancarsapi.security.internal.InternalAuthContext;
import com.example.cleancarsapi.service.FeaturePropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Runtime switches the console flips — its second write surface after the whitelist. */
@RestController
@RequestMapping("/internal/api/properties")
@RequiredArgsConstructor
public class InternalFeaturePropertyController {

    private final FeaturePropertyService featurePropertyService;

    @GetMapping
    public List<FeaturePropertyResponse> list() {
        return featurePropertyService.list().stream().map(this::toResponse).toList();
    }

    /** Create-or-update; an existing property must keep its JSON type (409 property_type_mismatch). */
    @PutMapping("/{key}")
    public FeaturePropertyResponse set(@PathVariable String key,
                                       @Valid @RequestBody FeaturePropertyUpdateRequest request) {
        FeatureProperty row = featurePropertyService.set(
                key, request.value(), request.description(), InternalAuthContext.require().email());
        return toResponse(row);
    }

    private FeaturePropertyResponse toResponse(FeatureProperty row) {
        return FeaturePropertyResponse.from(row, featurePropertyService.parse(row));
    }
}
