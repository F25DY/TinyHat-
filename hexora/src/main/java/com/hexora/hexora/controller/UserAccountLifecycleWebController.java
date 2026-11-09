package com.hexora.hexora.controller;

import com.hexora.hexora.dto.RecuperarRequest;
import com.hexora.hexora.dto.RegistroForm;
import com.hexora.hexora.dto.ResetPasswordForm;
import com.hexora.hexora.model.TokenSeguridad;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.SecurityTokenService;
import com.hexora.hexora.security.TinyHatSecurityProperties;
import com.hexora.hexora.security.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserAccountLifecycleWebController {

    private final UserRegistrationService registrationService;
    private final SecurityTokenService tokenService;
    private final TinyHatSecurityProperties securityProperties;

    public UserAccountLifecycleWebController(
        UserRegistrationService registrationService,
        SecurityTokenService tokenService,
        TinyHatSecurityProperties securityProperties
    ) {
        this.registrationService = registrationService;
        this.tokenService = tokenService;
        this.securityProperties = securityProperties;
    }

    // --- REGISTRATION ---

    @GetMapping("/registro")
    public String registroPage(Model model) {
        if (!securityProperties.getRegistration().isEnabled()) {
            throw new ScopedNotFoundException();
        }
        model.addAttribute("form", new RegistroForm());
        return "registro";
    }

    @PostMapping(value = "/registro", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> registerJson(
        @Valid @RequestBody RegistroForm form,
        HttpServletRequest request
    ) {
        UserRegistrationService.RegistrationResult result = registrationService.registerAuditor(form, request);
        return ResponseEntity.ok(Map.of("message", result.message()));
    }

    @PostMapping(value = "/registro")
    public String registerForm(
        @ModelAttribute RegistroForm form,
        HttpServletRequest request
    ) {
        registrationService.registerAuditor(form, request);
        return "redirect:/login?registered";
    }

    // --- EMAIL VERIFICATION ---

    @GetMapping("/verificar/{token}")
    public String verifyEmail(@PathVariable String token, HttpServletRequest request) {
        boolean verified = registrationService.verifyEmail(token, request);
        return verified ? "redirect:/login?verified" : "redirect:/login?invalidToken";
    }

    @GetMapping("/verificar-correo/{token}")
    public String verifyEmailAlias(@PathVariable String token, HttpServletRequest request) {
        return verifyEmail(token, request);
    }

    // --- INVITATION ACCEPTANCE ---

    @GetMapping("/invitacion/{token}")
    public String invitationPage(@PathVariable String token, Model model) {
        Optional<TokenSeguridad> valid = tokenService.findValidToken(token, SecurityTokenService.TIPO_INVITACION);
        if (valid.isEmpty()) {
            return "redirect:/login?invalidToken";
        }
        model.addAttribute("token", token);
        model.addAttribute("email", valid.get().getEmail());
        model.addAttribute("form", new ResetPasswordForm());
        return "invitacion";
    }

    @PostMapping(value = "/invitacion/{token}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> acceptInvitationJson(
        @PathVariable String token,
        @Valid @RequestBody ResetPasswordForm form,
        HttpServletRequest request
    ) {
        registrationService.acceptInvitation(token, form.getPassword(), request);
        return ResponseEntity.ok(Map.of("message", "Contraseña configurada con éxito. Ya puedes iniciar sesión."));
    }

    @PostMapping("/invitacion/{token}")
    public String acceptInvitationForm(
        @PathVariable String token,
        @RequestParam("password") String password,
        HttpServletRequest request
    ) {
        registrationService.acceptInvitation(token, password, request);
        return "redirect:/login?invited";
    }

    // --- PASSWORD RECOVERY ---

    @GetMapping("/recuperar")
    public String recuperarPage() {
        return "recuperar";
    }

    @PostMapping(value = "/recuperar", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> recuperarJson(
        @Valid @RequestBody RecuperarRequest request,
        HttpServletRequest httpRequest
    ) {
        registrationService.requestPasswordReset(request.getEmail(), httpRequest);
        return ResponseEntity.ok(Map.of(
            "message",
            "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña."
        ));
    }

    @PostMapping("/recuperar")
    public String recuperarForm(
        @RequestParam("email") String email,
        HttpServletRequest request
    ) {
        registrationService.requestPasswordReset(email, request);
        return "redirect:/recuperar?sent";
    }

    @GetMapping("/recuperar/{token}")
    public String resetPasswordPage(@PathVariable String token, Model model) {
        Optional<TokenSeguridad> valid = tokenService.findValidToken(token, SecurityTokenService.TIPO_RECUPERACION);
        if (valid.isEmpty()) {
            return "redirect:/login?invalidToken";
        }
        model.addAttribute("token", token);
        model.addAttribute("form", new ResetPasswordForm());
        return "recuperar-reset";
    }

    @PostMapping(value = "/recuperar/{token}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> resetPasswordJson(
        @PathVariable String token,
        @Valid @RequestBody ResetPasswordForm form,
        HttpServletRequest request
    ) {
        registrationService.resetPassword(token, form.getPassword(), request);
        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida con éxito."));
    }

    @PostMapping("/recuperar/{token}")
    public String resetPasswordForm(
        @PathVariable String token,
        @RequestParam("password") String password,
        HttpServletRequest request
    ) {
        registrationService.resetPassword(token, password, request);
        return "redirect:/login?reset";
    }
}

