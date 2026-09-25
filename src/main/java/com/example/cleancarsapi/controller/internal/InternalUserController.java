package com.example.cleancarsapi.controller.internal;

import com.example.cleancarsapi.dto.PageResponse;
import com.example.cleancarsapi.dto.internal.InternalUserOrgResponse;
import com.example.cleancarsapi.dto.internal.InternalUserSummaryResponse;
import com.example.cleancarsapi.service.internal.UserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** User views for the internal console (account users across every org). */
@RestController
@RequestMapping("/internal/api/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserReadService userReadService;

    @GetMapping
    public PageResponse<InternalUserSummaryResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(userReadService.list(search, pageable));
    }

    @GetMapping("/{id}/org")
    public InternalUserOrgResponse findOrg(@PathVariable UUID id) {
        return userReadService.findOrg(id);
    }
}
