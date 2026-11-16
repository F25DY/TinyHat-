package com.hexora.hexora.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.EntityListeners;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "vulnerabilidad")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class Vulnerabilidad implements TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(length = 100)
    private String herramienta;

    @Column(name = "host_afectado", length = 150)
    private String host;

    @Column(name = "ip_afectada", length = 45)
    private String ipAfectada;

    @Column(name = "puerto_afectado")
    private Integer puerto;

    @Column(length = 100)
    private String servicio;

    @Column(length = 30)
    private String cve;

    @Column(nullable = false)
    private int confidencialidad;

    @Column(nullable = false)
    private int integridad;

    @Column(nullable = false)
    private int disponibilidad;

    @Column(nullable = false)
    private int probabilidad;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal impacto;

    @Column(name = "nivel_riesgo", nullable = false, length = 20)
    private String nivelRiesgo;

    @Column(name = "plan_mitigacion", columnDefinition = "TEXT")
    private String planMitigacion;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "fecha_descubrimiento", nullable = false)
    private LocalDate fechaDeteccion;

    @ManyToOne
    @JoinColumn(name = "id_area", nullable = false)
    private AreaAuditable areaAuditable;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    //Constructores

    public Vulnerabilidad() {
    }
    
    public Vulnerabilidad(Long id, String nombre, String tipo, String herramienta, String host, String ipAfectada,
            int puerto, String servicio, String cve, int confidencialidad, int integridad, int disponibilidad,
            int probabilidad, BigDecimal impacto, String nivelRiesgo, String planMitigacion, String estado,
            LocalDate fechaDeteccion, AreaAuditable areaAuditable) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.herramienta = herramienta;
        this.host = host;
        this.ipAfectada = ipAfectada;
        this.puerto = puerto;
        this.servicio = servicio;
        this.cve = cve;
        this.confidencialidad = confidencialidad;
        this.integridad = integridad;
        this.disponibilidad = disponibilidad;
        this.probabilidad = probabilidad;
        this.impacto = impacto;
        this.nivelRiesgo = nivelRiesgo;
        this.planMitigacion = planMitigacion;
        this.estado = estado;
        this.fechaDeteccion = fechaDeteccion;
        this.areaAuditable = areaAuditable;
    }

    //Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getHerramienta() {
        return herramienta;
    }

    public void setHerramienta(String herramienta) {
        this.herramienta = herramienta;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getIpAfectada() {
        return ipAfectada;
    }

    public void setIpAfectada(String ipAfectada) {
        this.ipAfectada = ipAfectada;
    }

    public Integer getPuerto() {
        return puerto;
    }

    public void setPuerto(Integer puerto) {
        this.puerto = puerto;
    }

    public String getServicio() {
        return servicio;
    }

    public void setServicio(String servicio) {
        this.servicio = servicio;
    }

    public String getCve() {
        return cve;
    }

    public void setCve(String cve) {
        this.cve = cve;
    }

    public int getConfidencialidad() {
        return confidencialidad;
    }

    public void setConfidencialidad(int confidencialidad) {
        this.confidencialidad = confidencialidad;
    }

    public int getIntegridad() {
        return integridad;
    }

    public void setIntegridad(int integridad) {
        this.integridad = integridad;
    }

    public int getDisponibilidad() {
        return disponibilidad;
    }

    public void setDisponibilidad(int disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    public int getProbabilidad() {
        return probabilidad;
    }

    public void setProbabilidad(int probabilidad) {
        this.probabilidad = probabilidad;
    }

    public BigDecimal getImpacto() {
        return impacto;
    }

    public void setImpacto(BigDecimal impacto) {
        this.impacto = impacto;
    }

    public String getNivelRiesgo() {
        return nivelRiesgo;
    }

    public String getRiesgo() {
        return nivelRiesgo;
    }

    public void setNivelRiesgo(String nivelRiesgo) {
        this.nivelRiesgo = nivelRiesgo;
    }

    public String getPlanMitigacion() {
        return planMitigacion;
    }

    public void setPlanMitigacion(String planMitigacion) {
        this.planMitigacion = planMitigacion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getFechaDeteccion() {
        return fechaDeteccion;
    }

    public void setFechaDeteccion(LocalDate fechaDeteccion) {
        this.fechaDeteccion = fechaDeteccion;
    }

    public AreaAuditable getAreaAuditable() {
        return areaAuditable;
    }

    public void setAreaAuditable(AreaAuditable areaAuditable) {
        this.areaAuditable = areaAuditable;
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
