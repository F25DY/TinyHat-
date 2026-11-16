package com.hexora.network.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.EntityListeners;
import com.hexora.hexora.model.TenantScopedEntity;
import com.hexora.hexora.model.TenantScopedEntityListener;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "monitored_port", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_monitored_port_cuenta_host_port",
        columnNames = {"cuenta_id", "network_host_id", "port"})
})
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class MonitoredPort implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_host_id", nullable = false)
    private NetworkHost host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    public Long getId() {
        return id;
    }

    public NetworkHost getHost() {
        return host;
    }

    public void setHost(NetworkHost host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    @jakarta.persistence.PrePersist
    void generatePublicId() {
        publicId = com.hexora.hexora.model.PublicIds.ensure(publicId);
    }
}
