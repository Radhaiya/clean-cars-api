package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.ReferenceDataResponse;
import com.example.cleancarsapi.service.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (permit-all) pick-lists for the org form — needed before the org exists. */
@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    @GetMapping
    public ReferenceDataResponse list() {
        return referenceDataService.list();
    }
}
