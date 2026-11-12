package com.hexora.hexora.security;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;
import java.util.regex.Pattern;

/**
 * Logback conversion word {@code %mask(...)}: removes credentials, tokens and session identifiers
 * from log messages before they reach the console or the log file.
 *
 * <p>Covered: values of password/secret/token/api-key/session keys written as {@code key=value},
 * {@code key: value} or JSON, entire Authorization/Cookie/Set-Cookie header values, TOTP codes passed
 * as {@code code=NNNNNN}, one-time links ({@code /verificar/<token>}, {@code /recuperar/<token>},
 * {@code /invitacion/<token>}) and any standalone 43-character base64url word, which is the exact
 * length of the 32-byte tokens this application issues.
 */
public class LogMaskingConverter extends CompositeConverter<ILoggingEvent> {

    static final String MASK = "***";

    private static final Pattern WHOLE_VALUE_HEADERS = Pattern.compile(
        "(?i)\\b(authorization|set-cookie|cookie)\\b(\\s*[=:]\\s*)[^\\r\\n]*");
    private static final Pattern SECRET_KEYS = Pattern.compile(
        "(?i)(\\b(?:password|passwd|pwd|secret|client[-_]?secret|token|access[-_]?token|refresh[-_]?token|"
            + "api[-_]?key|apikey|tinyhat_sid|jsessionid|session[-_]?id|totp|otp|mfa[-_]?code|recovery[-_]?code)\\b"
            + "[\"']?\\s*[=:]\\s*[\"']?)[^\\s,;&\"'}\\]]+");
    private static final Pattern TOTP_PARAMETER = Pattern.compile("(?i)(\\bcode=)\\d{6,8}\\b");
    private static final Pattern ONE_TIME_LINK = Pattern.compile(
        "(?i)(/(?:verificar|verificar-correo|recuperar|invitacion)/)[A-Za-z0-9_-]{16,}");
    private static final Pattern TOKEN_SHAPED_WORD = Pattern.compile(
        "(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])");

    /** Receives the already-rendered inner pattern, for example the text produced by {@code %m}. */
    @Override
    protected String transform(ILoggingEvent event, String in) {
        return mask(in);
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = WHOLE_VALUE_HEADERS.matcher(message).replaceAll("$1$2" + MASK);
        masked = SECRET_KEYS.matcher(masked).replaceAll("$1" + MASK);
        masked = TOTP_PARAMETER.matcher(masked).replaceAll("$1" + MASK);
        masked = ONE_TIME_LINK.matcher(masked).replaceAll("$1" + MASK);
        return TOKEN_SHAPED_WORD.matcher(masked).replaceAll(MASK);
    }
}
