package com.hexora.hexora.security;

public interface Notifier {

    void sendVerificationEmail(String email, String verificationUrl);

    void sendInvitationEmail(String email, String invitationUrl, String organizationName);

    void sendPasswordResetEmail(String email, String resetUrl);

    void sendAccountAlreadyExistsNotification(String email);

    /** Aviso de informe nuevo: sin nombre de proyecto, hallazgos ni adjuntos; solo el enlace de inicio de sesión. */
    void sendReportAvailableEmail(String email, String loginUrl);
}

