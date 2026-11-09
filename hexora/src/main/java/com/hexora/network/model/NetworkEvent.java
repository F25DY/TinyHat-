package com.hexora.network.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.EntityListeners;
import java.time.LocalDateTime;
import com.hexora.hexora.model.PublicIds;
import com.hexora.hexora.model.TenantScopedEntity;
import com.hexora.hexora.model.TenantScopedEntityListener;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "network_event")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class NetworkEvent implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_host_id", nullable = false)
    private NetworkHost host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "network_scan_id")
    private NetworkScan scan;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private NetworkEventType eventType;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NetworkHost getHost() {
        return host;
    }

    public void setHost(NetworkHost host) {
        this.host = host;
    }

    public NetworkScan getScan() {
        return scan;
    }

    public void setScan(NetworkScan scan) {
        this.scan = scan;
    }

    public NetworkEventType getEventType() {
        return eventType;
    }

    public void setEventType(NetworkEventType eventType) {
        this.eventType = eventType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    @Override
    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    @PrePersist
    void generatePublicId() {
        publicId = PublicIds.ensure(publicId);
    }
}
