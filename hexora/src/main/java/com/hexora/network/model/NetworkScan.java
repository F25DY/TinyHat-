package com.hexora.network.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.hexora.hexora.model.PublicIds;

@Entity
@Table(name = "network_scan")
public class NetworkScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private NetworkHost host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NetworkScanStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "host_status", nullable = false, length = 20)
    private NetworkHostStatus hostStatus;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "ports_scanned", nullable = false)
    private int portsScanned;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "cuenta_id", nullable = false, insertable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortScanResult> portResults = new ArrayList<>();

    @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NetworkEvent> events = new ArrayList<>();

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

    public NetworkScanStatus getStatus() {
        return status;
    }

    public void setStatus(NetworkScanStatus status) {
        this.status = status;
    }

    public NetworkHostStatus getHostStatus() {
        return hostStatus;
    }

    public void setHostStatus(NetworkHostStatus hostStatus) {
        this.hostStatus = hostStatus;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public int getPortsScanned() {
        return portsScanned;
    }

    public void setPortsScanned(int portsScanned) {
        this.portsScanned = portsScanned;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getCuentaId() {
        return cuentaId;
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

    public List<PortScanResult> getPortResults() {
        return portResults;
    }

    public void addPortResult(PortScanResult result) {
        portResults.add(result);
        result.setScan(this);
    }

    public List<NetworkEvent> getEvents() {
        return events;
    }

    public void addEvent(NetworkEvent event) {
        events.add(event);
        event.setScan(this);
    }
}
