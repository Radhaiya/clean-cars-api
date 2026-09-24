package com.example.cleancarsapi.controller;

import com.example.cleancarsapi.dto.AcceptInviteResponse;
import com.example.cleancarsapi.dto.InviteRequest;
import com.example.cleancarsapi.dto.InviteResponse;
import com.example.cleancarsapi.service.InviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Org invites. Sender endpoints (create / list / revoke) are owner-only — the
 * service enforces it via {@code AuthContext.require(UserRole.OWNER)}. Invitee
 * endpoints ({@code /me}, accept, decline) are open to any authenticated caller.
 * See docs/FEATURE-INVITES.md.
 */
@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    /** Owner sends an invite to an email address. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse create(@Valid @RequestBody InviteRequest request) {
        return inviteService.create(request);
    }

    /** Owner: all invites this org ever sent, newest first. */
    @GetMapping
    public List<InviteResponse> list() {
        return inviteService.list();
    }

    /** Owner: cancel a pending invite. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        inviteService.revoke(id);
    }

    /** Invitee: my pending, unexpired invites (after signing in). */
    @GetMapping("/me")
    public List<InviteResponse> myInvites() {
        return inviteService.myInvites();
    }

    /** Invitee: join the org; returns a fresh token carrying the new org_id/role. */
    @PostMapping("/{id}/accept")
    public AcceptInviteResponse accept(@PathVariable UUID id) {
        return inviteService.accept(id);
    }

    /** Invitee: deny the invite (re-invitable afterwards). */
    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable UUID id) {
        inviteService.decline(id);
    }
}
