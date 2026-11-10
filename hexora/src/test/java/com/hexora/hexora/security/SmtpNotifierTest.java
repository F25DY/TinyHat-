package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpNotifierTest {

    private static final String FROM = "no-reply@example.com";
    private static final String RECIPIENT = "user@example.com";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    private final SmtpNotifier notifier = new SmtpNotifier(mailSender, FROM);

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    @Test
    void sendsVerificationEmailWithUtf8ContentAndLink() throws Exception {
        notifier.sendVerificationEmail(RECIPIENT, "https://hexora.example/verificar/token");

        assertMessage(
            "Verifica tu cuenta de Hexora",
            "https://hexora.example/verificar/token"
        );
    }

    @Test
    void sendsInvitationWithOrganizationNameAndLink() throws Exception {
        notifier.sendInvitationEmail(
            RECIPIENT,
            "https://hexora.example/invitacion/token",
            "Organización de prueba"
        );

        assertMessage(
            "Invitación para unirte a Hexora",
            "Organización de prueba",
            "https://hexora.example/invitacion/token"
        );
    }

    @Test
    void sendsPasswordResetWithLinkAndSafetyNotice() throws Exception {
        notifier.sendPasswordResetEmail(RECIPIENT, "https://hexora.example/recuperar/token");

        assertMessage(
            "Restablece tu contraseña de Hexora",
            "https://hexora.example/recuperar/token",
            "puedes ignorar este mensaje"
        );
    }

    @Test
    void sendsNeutralExistingAccountNotice() throws Exception {
        notifier.sendAccountAlreadyExistsNotification(RECIPIENT);

        assertMessage(
            "Aviso sobre tu cuenta de Hexora",
            "ya existe una cuenta asociada"
        );
    }

    @Test
    void propagatesSmtpDeliveryFailures() {
        MailSendException failure = new MailSendException("SMTP unavailable");
        doThrow(failure).when(mailSender).send(message);

        assertThatThrownBy(() -> notifier.sendVerificationEmail(RECIPIENT, "https://example.com/token"))
            .isSameAs(failure);
    }

    private void assertMessage(String expectedSubject, String... expectedBodyParts) throws Exception {
        verify(mailSender).send(message);
        assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(RECIPIENT);
        assertThat(message.getSubject()).isEqualTo(expectedSubject);
        assertThat(message.getContent()).isInstanceOf(String.class);
        String body = (String) message.getContent();
        for (String expectedBodyPart : expectedBodyParts) {
            assertThat(body).contains(expectedBodyPart);
        }
    }
}
