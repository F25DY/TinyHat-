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
@Table(name = "network_discovery_snapshot_device")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class NetworkDiscoverySnapshotDevice implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private NetworkDiscoverySnapshot snapshot;

    @Column(nullable = false, length = 45)
    private String address;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(name = "host_name", nullable = false, length = 255)
    private String hostName;

    @Column(name = "detection_method", nullable = false, length = 255)
    private String detectionMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NetworkHostStatus status;

    @Column(nullable = false)
    private boolean registered;

    @Column(name = "first_detected_at", nullable = false)
    private LocalDateTime firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private LocalDateTime lastDetectedAt;

    @Column(name = "consecutive_misses", nullable = false)
    private int consecutiveMisses;

    @Column(nullable = false)
    private boolean gateway;

    @Column(name = "gateway_address", length = 45)
    private String gatewayAddress;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    public Long getId() {
        return id;
    }

    public NetworkDiscoverySnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(NetworkDiscoverySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getDetectionMethod() {
        return detectionMethod;
    }

    public void setDetectionMethod(String detectionMethod) {
        this.detectionMethod = detectionMethod;
    }

    public NetworkHostStatus getStatus() {
        return status;
    }

    public void setStatus(NetworkHostStatus status) {
        this.status = status;
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public LocalDateTime getFirstDetectedAt() {
        return firstDetectedAt;
    }

    public void setFirstDetectedAt(LocalDateTime firstDetectedAt) {
        this.firstDetectedAt = firstDetectedAt;
    }

    public LocalDateTime getLastDetectedAt() {
        return lastDetectedAt;
    }

    public void setLastDetectedAt(LocalDateTime lastDetectedAt) {
        this.lastDetectedAt = lastDetectedAt;
    }

    public int getConsecutiveMisses() {
        return consecutiveMisses;
    }

    public void setConsecutiveMisses(int consecutiveMisses) {
        this.consecutiveMisses = consecutiveMisses;
    }

    public boolean isGateway() {
        return gateway;
    }

    public void setGateway(boolean gateway) {
        this.gateway = gateway;
    }

    public String getGatewayAddress() {
        return gatewayAddress;
    }

    public void setGatewayAddress(String gatewayAddress) {
        this.gatewayAddress = gatewayAddress;
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
