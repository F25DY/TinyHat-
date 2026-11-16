package com.hexora.network.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.EntityListeners;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.hexora.hexora.model.PublicIds;
import com.hexora.hexora.model.TenantScopedEntity;
import com.hexora.hexora.model.TenantScopedEntityListener;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "network_discovery_snapshot")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class NetworkDiscoverySnapshot implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 18)
    private String subnet;

    @Column(nullable = false, length = 255)
    private String source;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("address ASC")
    private List<NetworkDiscoverySnapshotDevice> devices = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public String getSubnet() {
        return subnet;
    }

    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
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

    public List<NetworkDiscoverySnapshotDevice> getDevices() {
        return devices;
    }

    public void addDevice(NetworkDiscoverySnapshotDevice device) {
        devices.add(device);
        device.setSnapshot(this);
    }
}
