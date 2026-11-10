package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "mfa_codigo_recuperacion")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "hash", nullable = false, length = 100)
    private String hash;

    @Column(name = "usado_en")
    private LocalDateTime usedAt;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime createdAt;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(Long usuarioId, String hash) {
        this.usuarioId = usuarioId;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getHash() {
        return hash;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
