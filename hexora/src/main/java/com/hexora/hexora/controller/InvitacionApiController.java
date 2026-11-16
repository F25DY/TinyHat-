package com.hexora.hexora.controller;

import com.hexora.hexora.dto.InvitacionRequest;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.security.RequireRecentAuth;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizaciones")
public class InvitacionApiController {

    private final UserRegistrationService registrationService;

    public InvitacionApiController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/{publicId}/invitaciones")
    @PreAuthorize("hasRole('AUDITOR')")
    @RequireRecentAuth(maxAgeMinutes = 5)
    public ResponseEntity<Map<String, Object>> invitar(
        @PathVariable String publicId,
        @Valid @RequestBody InvitacionRequest request,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        Usuario usuario = registrationService.inviteClientAdmin(
            publicId, request.getEmail(), principal, httpRequest
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "status", "INVITED",
            "email", usuario.getEmailNormalizado(),
            "role", usuario.getRol(),
            "userPublicId", usuario.getPublicId()
        ));
    }
}

