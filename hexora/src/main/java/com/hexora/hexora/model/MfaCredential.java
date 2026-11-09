package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "mfa_credencial")
public class MfaCredential {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "secreto_cifrado", nullable = false, columnDefinition = "VARBINARY(64)")
    private byte[] encryptedSecret;

    @Column(nullable = false, columnDefinition = "BINARY(12)")
    private byte[] nonce;

    @Column(name = "ultimo_paso_usado")
    private Long lastStepUsed;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime createdAt;

    protected MfaCredential() {
    }

    public MfaCredential(Long usuarioId, byte[] encryptedSecret, byte[] nonce) {
        this.usuarioId = usuarioId;
        this.encryptedSecret = encryptedSecret;
        this.nonce = nonce;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public byte[] getEncryptedSecret() {
        return encryptedSecret;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public Long getLastStepUsed() {
        return lastStepUsed;
    }

    public boolean isActivo() {
        return activo;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
