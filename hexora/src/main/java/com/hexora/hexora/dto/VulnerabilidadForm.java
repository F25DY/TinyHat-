package com.hexora.hexora.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class VulnerabilidadForm {

    @NotBlank
    @Size(max = 150)
    private String nombre;

    @NotBlank
    @Size(max = 100)
    private String tipo;

    @Size(max = 100)
    private String herramienta;

    @Size(max = 150)
    private String host;

    @Pattern(regexp = "^$|^[0-9a-fA-F:.]{1,45}$")
    private String ipAfectada;

    @Min(1)
    @Max(65535)
    private Integer puerto;

    @Size(max = 100)
    private String servicio;

    @Size(max = 30)
    private String cve;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer confidencialidad;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer integridad;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer disponibilidad;

    @NotNull
    @Min(0)
    @Max(10)
    private Integer probabilidad;

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "Abierta|En Progreso|Mitigada|Aceptada")
    private String estado;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaDeteccion;

    @Size(max = 65535)
    private String planMitigacion;

    private BigDecimal impacto;
    private String nivelRiesgo;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getHerramienta() { return herramienta; }
    public void setHerramienta(String herramienta) { this.herramienta = herramienta; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getIpAfectada() { return ipAfectada; }
    public void setIpAfectada(String ipAfectada) { this.ipAfectada = ipAfectada; }
    public Integer getPuerto() { return puerto; }
    public void setPuerto(Integer puerto) { this.puerto = puerto; }
    public String getServicio() { return servicio; }
    public void setServicio(String servicio) { this.servicio = servicio; }
    public String getCve() { return cve; }
    public void setCve(String cve) { this.cve = cve; }
    public Integer getConfidencialidad() { return confidencialidad; }
    public void setConfidencialidad(Integer confidencialidad) { this.confidencialidad = confidencialidad; }
    public Integer getIntegridad() { return integridad; }
    public void setIntegridad(Integer integridad) { this.integridad = integridad; }
    public Integer getDisponibilidad() { return disponibilidad; }
    public void setDisponibilidad(Integer disponibilidad) { this.disponibilidad = disponibilidad; }
    public Integer getProbabilidad() { return probabilidad; }
    public void setProbabilidad(Integer probabilidad) { this.probabilidad = probabilidad; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDate getFechaDeteccion() { return fechaDeteccion; }
    public void setFechaDeteccion(LocalDate fechaDeteccion) { this.fechaDeteccion = fechaDeteccion; }
    public String getPlanMitigacion() { return planMitigacion; }
    public void setPlanMitigacion(String planMitigacion) { this.planMitigacion = planMitigacion; }
    public BigDecimal getImpacto() { return impacto; }
    public void setImpacto(BigDecimal impacto) { this.impacto = impacto; }
    public String getNivelRiesgo() { return nivelRiesgo; }
    public void setNivelRiesgo(String nivelRiesgo) { this.nivelRiesgo = nivelRiesgo; }
}
