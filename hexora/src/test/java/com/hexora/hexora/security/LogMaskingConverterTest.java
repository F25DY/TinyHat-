package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LogMaskingConverterTest {

    private static final String TOKEN = "3fGe4_hGUxEL-HdodKcojZjjeGdXhh1vkysH95UmTjA";

    @Test
    void masksCredentialsInKeyValueAndJsonForms() {
        assertThat(LogMaskingConverter.mask("login password=Sup3rS3cret! email=a@b.c"))
            .doesNotContain("Sup3rS3cret").contains("password=***");
        assertThat(LogMaskingConverter.mask("{\"password\":\"Sup3rS3cret!\",\"user\":\"x\"}"))
            .doesNotContain("Sup3rS3cret");
        assertThat(LogMaskingConverter.mask("client_secret: abc123XYZ api-key=KEY-0001 token=" + TOKEN))
            .doesNotContain("abc123XYZ").doesNotContain("KEY-0001").doesNotContain(TOKEN);
    }

    @Test
    void masksHeadersSessionIdentifiersAndTotpCodes() {
        assertThat(LogMaskingConverter.mask("Authorization: Bearer eyJhbGciOi.payload.sig"))
            .isEqualTo("Authorization: ***");
        assertThat(LogMaskingConverter.mask("Cookie: TINYHAT_SID=ABCDEF123456; other=1"))
            .doesNotContain("ABCDEF123456");
        assertThat(LogMaskingConverter.mask("Set-Cookie: TINYHAT_SID=ABCDEF123456; Path=/; Secure"))
            .doesNotContain("ABCDEF123456");
        assertThat(LogMaskingConverter.mask("session replaced JSESSIONID=1A2B3C4D5E6F"))
            .doesNotContain("1A2B3C4D5E6F");
        assertThat(LogMaskingConverter.mask("POST /mfa/verify code=123456")).doesNotContain("123456");
        assertThat(LogMaskingConverter.mask("totp=654321 recovery_code=ABCD-EFGH"))
            .doesNotContain("654321").doesNotContain("ABCD-EFGH");
    }

    @Test
    void masksOneTimeLinksAndTokenShapedWords() {
        assertThat(LogMaskingConverter.mask("GET /verificar/" + TOKEN))
            .isEqualTo("GET /verificar/***");
        assertThat(LogMaskingConverter.mask("https://localhost:8443/recuperar/" + TOKEN + "?x=1"))
            .doesNotContain(TOKEN);
        assertThat(LogMaskingConverter.mask("link=/invitacion/" + TOKEN)).doesNotContain(TOKEN);
        assertThat(LogMaskingConverter.mask("unexpected value " + TOKEN + " in message"))
            .doesNotContain(TOKEN);
    }

    @Test
    void leavesOrdinaryDiagnosticTextUntouched() {
        String correlationId = UUID.randomUUID().toString();
        String sha256 = "a".repeat(64);
        String message = "Request failed; correlationId=" + correlationId
            + ", exceptionType=com.hexora.hexora.security.ScopedNotFoundException, hash=" + sha256
            + ", accountId=7, GET /proyectos/" + correlationId;

        assertThat(LogMaskingConverter.mask(message)).isEqualTo(message);
        assertThat(LogMaskingConverter.mask(null)).isNull();
        assertThat(LogMaskingConverter.mask("")).isEmpty();
    }

    /** The pattern used by logback-spring.xml: masked text, one line per event, exceptions masked too. */
    @Test
    void theProductionPatternKeepsLineBreaksAndMasksExceptionMessages() {
        LoggerContext context = new LoggerContext();
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.getInstanceConverterMap().put("mask", LogMaskingConverter::new);
        layout.setPattern("%5p : %mask(%m%n%ex)");
        layout.start();

        String first = layout.doLayout(new LoggingEvent("x", context.getLogger("probe"), Level.INFO,
            "login password=Sup3rS3cret!", null, null));
        String second = layout.doLayout(new LoggingEvent("x", context.getLogger("probe"), Level.ERROR,
            "failed", new IllegalStateException("boom token=" + TOKEN), null));

        assertThat(first).endsWith(System.lineSeparator()).doesNotContain("Sup3rS3cret").doesNotContain("%n");
        assertThat(second).doesNotContain(TOKEN).contains("IllegalStateException").contains("token=***");
        assertThat(second.lines().count()).isGreaterThan(2);
    }
}
