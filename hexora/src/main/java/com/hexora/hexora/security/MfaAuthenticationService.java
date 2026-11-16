package com.hexora.hexora.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class MfaAuthenticationService {

    public static final String LAST_REAUTH_ATTRIBUTE = "TH_LAST_REAUTH";
    public static final String ATTEMPTS_ATTRIBUTE = MfaAuthenticationService.class.getName() + ".attempts";
    public static final String REAUTH_TARGET_ATTRIBUTE = "TH_REAUTH_TARGET";

    private final SecurityContextRepository securityContextRepository;
    private final TinyHatSecurityProperties properties;
    private final Clock clock;

    public MfaAuthenticationService(
        SecurityContextRepository securityContextRepository,
        TinyHatSecurityProperties properties,
        Clock clock
    ) {
        this.securityContextRepository = securityContextRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public void completeMfa(
        TinyHatPrincipal principal,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()
        );
        replaceAuthentication(authenticated, request, response, true);
        HttpSession session = request.getSession();
        long now = clock.millis();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
        session.setAttribute(LAST_REAUTH_ATTRIBUTE, now);
        session.removeAttribute(ATTEMPTS_ATTRIBUTE);
    }

    public void completeReauthentication(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        replaceAuthentication(current, request, response, false);
        HttpSession session = request.getSession();
        long now = clock.millis();
        session.setAttribute(LAST_REAUTH_ATTRIBUTE, now);
        session.removeAttribute(ATTEMPTS_ATTRIBUTE + ".reauth");
    }

    public boolean hasRecentAuthentication(HttpSession session, int maxAgeMinutes) {
        Object value = session.getAttribute(LAST_REAUTH_ATTRIBUTE);
        if (!(value instanceof Number timestamp)) {
            return false;
        }
        long ageMillis = clock.millis() - timestamp.longValue();
        long maxAgeMillis = maxAgeMinutes * 60_000L;
        return ageMillis >= 0 && ageMillis < maxAgeMillis;
    }

    public int recordFailure(HttpSession session, String attemptType) {
        String attributeName = ATTEMPTS_ATTRIBUTE + "." + attemptType;
        int attempts = session.getAttribute(attributeName) instanceof Number number
            ? number.intValue()
            : 0;
        attempts++;
        session.setAttribute(attributeName, attempts);
        return attempts;
    }

    public int maxAttempts() {
        return properties.getMfa().getMaxAttempts();
    }

    private void replaceAuthentication(
        Authentication authentication,
        HttpServletRequest request,
        HttpServletResponse response,
        boolean rotateSessionId
    ) throws IOException {
        HttpSession session = request.getSession();
        if (rotateSessionId) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
