package com.hexora.hexora.controller;

import com.hexora.hexora.security.AuditService;
import com.hexora.hexora.security.MfaAuthenticationService;
import com.hexora.hexora.security.MfaService;
import com.hexora.hexora.security.RequireRecentAuth;
import com.hexora.hexora.security.SessionActivityFilter;
import com.hexora.hexora.security.TinyHatPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MfaController {

    private final MfaService mfaService;
    private final MfaAuthenticationService mfaAuthenticationService;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;

    public MfaController(
        MfaService mfaService,
        MfaAuthenticationService mfaAuthenticationService,
        PasswordEncoder passwordEncoder,
        SessionRegistry sessionRegistry,
        AuditService auditService
    ) {
        this.mfaService = mfaService;
        this.mfaAuthenticationService = mfaAuthenticationService;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
    }

    @GetMapping("/mfa/enroll")
    @PreAuthorize("hasAuthority('ROLE_PRE_MFA')")
    public String enrollPage(Authentication authentication, Model model) {
        if (!isPreMfa(authentication)) {
            return "redirect:/mfa/verify";
        }
        TinyHatPrincipal principal = principal(authentication);
        model.addAttribute("pending", mfaService.isPending(principal.getUserId()));
        return "mfa/enroll";
    }

    @PostMapping("/mfa/enroll/start")
    @PreAuthorize("hasAuthority('ROLE_PRE_MFA')")
    public String startEnrollment(Authentication authentication, Model model) {
        if (!isPreMfa(authentication)) {
            return "redirect:/mfa/verify";
        }
        TinyHatPrincipal principal = principal(authentication);
        if (mfaService.isActive(principal.getUserId())) {
            return "redirect:/mfa/verify";
        }
        MfaService.Enrollment enrollment = mfaService.beginEnrollment(principal);
        model.addAttribute("pending", true);
        model.addAttribute("provisioningUri", enrollment.provisioningUri());
        model.addAttribute("secret", enrollment.secret());
        return "mfa/enroll";
    }

    @PostMapping("/mfa/enroll/confirm")
    @PreAuthorize("hasAuthority('ROLE_PRE_MFA')")
    public String confirmEnrollment(
        Authentication authentication,
        @RequestParam("code") String code,
        HttpServletRequest request,
        HttpServletResponse response,
        Model model
    ) throws IOException {
        if (!isPreMfa(authentication)) {
            return "redirect:/mfa/verify";
        }
        TinyHatPrincipal principal = principal(authentication);
        List<String> recoveryCodes = mfaService.confirmEnrollment(principal.getUserId(), code);
        if (recoveryCodes.isEmpty()) {
            return failedMfaAttempt(principal, request, response, model, "enroll");
        }
        mfaAuthenticationService.completeMfa(principal, request, response);
        model.addAttribute("recoveryCodes", recoveryCodes);
        return "mfa/recovery-codes";
    }

    @GetMapping("/mfa/verify")
    @PreAuthorize("hasAuthority('ROLE_PRE_MFA')")
    public String verifyPage(Authentication authentication) {
        if (!isPreMfa(authentication)) {
            return "redirect:/dashboard";
        }
        TinyHatPrincipal principal = principal(authentication);
        return mfaService.isActive(principal.getUserId())
            ? "mfa/verify"
            : "redirect:/mfa/enroll";
    }

    @PostMapping("/mfa/verify")
    @PreAuthorize("hasAuthority('ROLE_PRE_MFA')")
    public String verify(
        Authentication authentication,
        @RequestParam("code") String code,
        HttpServletRequest request,
        HttpServletResponse response,
        Model model
    ) throws IOException {
        if (!isPreMfa(authentication)) {
            return "redirect:/dashboard";
        }
        TinyHatPrincipal principal = principal(authentication);
        if (!mfaService.verifyLoginCode(principal.getUserId(), code)) {
            return failedMfaAttempt(principal, request, response, model, "verify");
        }
        mfaAuthenticationService.completeMfa(principal, request, response);
        return "redirect:/dashboard";
    }

    @GetMapping("/mfa/reauth")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public String reauthenticationPage(Authentication authentication, Model model) {
        if (isPreMfa(authentication)) {
            return "redirect:/mfa/verify";
        }
        model.addAttribute("required", true);
        return "mfa/reauth";
    }

    @PostMapping("/mfa/reauth")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public String reauthenticate(
        Authentication authentication,
        @RequestParam("password") String password,
        @RequestParam("code") String code,
        HttpServletRequest request,
        HttpServletResponse response,
        Model model
    ) throws IOException {
        if (isPreMfa(authentication)) {
            return "redirect:/mfa/verify";
        }
        TinyHatPrincipal principal = principal(authentication);
        if (!passwordEncoder.matches(password, principal.getPassword())
            || !mfaService.verifyTotpForReauthentication(principal.getUserId(), code)) {
            return failedMfaAttempt(principal, request, response, model, "reauth");
        }

        mfaAuthenticationService.completeReauthentication(request, response);
        HttpSession session = request.getSession(false);
        Object target = session == null
            ? null
            : session.getAttribute(MfaAuthenticationService.REAUTH_TARGET_ATTRIBUTE);
        if (session != null) {
            session.removeAttribute(MfaAuthenticationService.REAUTH_TARGET_ATTRIBUTE);
        }
        return safeTarget(target, request) ? "redirect:" + target : "redirect:/dashboard";
    }

    @GetMapping("/mfa/reset")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    @RequireRecentAuth(maxAgeMinutes = 5)
    public String resetPage(Authentication authentication) {
        return isPreMfa(authentication) ? "redirect:/mfa/verify" : "mfa/reset";
    }

    @PostMapping("/mfa/reset")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    @RequireRecentAuth(maxAgeMinutes = 5)
    public String reset(
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        if (isPreMfa(authentication)) {
            return "redirect:/mfa/verify";
        }
        TinyHatPrincipal principal = principal(authentication);
        mfaService.reset(principal.getUserId());
        auditService.mfaReset(principal, request);
        sessionRegistry.getAllSessions(principal, false).forEach(information -> information.expireNow());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/login?mfa=reset";
    }

    private String failedMfaAttempt(
        TinyHatPrincipal principal,
        HttpServletRequest request,
        HttpServletResponse response,
        Model model,
        String attemptType
    ) throws IOException {
        HttpSession session = request.getSession();
        int failures = mfaAuthenticationService.recordFailure(session, attemptType);
        if (failures >= mfaAuthenticationService.maxAttempts()) {
            sessionRegistry.getAllSessions(principal, false).forEach(information -> information.expireNow());
            session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + "/login?mfa=locked");
            return null;
        }
        model.addAttribute("error", "El código no es válido. Inténtalo nuevamente.");
        if ("enroll".equals(attemptType)) {
            model.addAttribute("pending", true);
        }
        return "mfa/" + attemptType;
    }

    private boolean isPreMfa(Authentication authentication) {
        return authentication != null
            && AuthorityUtils.authorityListToSet(authentication.getAuthorities()).contains("ROLE_PRE_MFA");
    }

    private TinyHatPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TinyHatPrincipal principal)) {
            throw new IllegalStateException("MFA requires an authenticated TinyHat principal");
        }
        return principal;
    }

    private boolean safeTarget(Object target, HttpServletRequest request) {
        if (!(target instanceof String path)) {
            return false;
        }
        String contextPath = request.getContextPath();
        return path.startsWith(contextPath + "/") && !path.startsWith(contextPath + "//");
    }
}
