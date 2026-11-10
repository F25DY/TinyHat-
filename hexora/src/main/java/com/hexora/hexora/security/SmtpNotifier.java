package com.hexora.hexora.security;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class SmtpNotifier implements Notifier {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpNotifier(
        JavaMailSender mailSender,
        @Value("${tinyhat.mail.from}") String fromAddress
    ) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender is required");
        this.fromAddress = Objects.requireNonNull(fromAddress, "fromAddress is required");
    }

    @Override
    public void sendVerificationEmail(String email, String verificationUrl) {
        send(
            email,
            "Verifica tu cuenta de Hexora",
            "Para verificar tu cuenta, abre este enlace:\n\n" + verificationUrl
        );
    }

    @Override
    public void sendInvitationEmail(String email, String invitationUrl, String organizationName) {
        send(
            email,
            "Invitación para unirte a Hexora",
            "Has sido invitado a unirte a " + organizationName
                + " en Hexora. Para aceptar la invitación, abre este enlace:\n\n"
                + invitationUrl
        );
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetUrl) {
        send(
            email,
            "Restablece tu contraseña de Hexora",
            "Recibimos una solicitud para restablecer tu contraseña. Para continuar, abre este enlace:\n\n"
                + resetUrl
                + "\n\nSi no solicitaste este cambio, puedes ignorar este mensaje."
        );
    }

    @Override
    public void sendAccountAlreadyExistsNotification(String email) {
        send(
            email,
            "Aviso sobre tu cuenta de Hexora",
            "Recibimos un intento de registro con esta dirección, pero ya existe una cuenta asociada. "
                + "Si no fuiste tú, puedes ignorar este mensaje."
        );
    }

    private void send(String recipient, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                false,
                StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Could not compose security email", exception);
        }
        mailSender.send(message);
    }
}
