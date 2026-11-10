package com.hexora.hexora.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ProyectoView {

    private final String id;
    private final String nombre;
    private final String empresa;
    private final String estado;
    private final LocalDate fechaInicio;
    private final String descripcion;
    private final List<DepartamentoView> departamentos;
    private final Map<String, Long> metricas;

    public ProyectoView(
            String id,
            String nombre,
            String empresa,
            String estado,
            LocalDate fechaInicio,
            String descripcion,
            List<DepartamentoView> departamentos,
            Map<String, Long> metricas) {
        this.id = id;
        this.nombre = nombre;
        this.empresa = empresa;
        this.estado = estado;
        this.fechaInicio = fechaInicio;
        this.descripcion = descripcion;
        this.departamentos = departamentos;
        this.metricas = metricas;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmpresa() {
        return empresa;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public List<DepartamentoView> getDepartamentos() {
        return departamentos;
    }

    public Map<String, Long> getMetricas() {
        return metricas;
    }

    public static class DepartamentoView {

        private final String id;
        private final String nombre;
        private final String descripcion;
        private final long vulnerabilidades;
        private final long criticas;

        public DepartamentoView(String id, String nombre, String descripcion, long vulnerabilidades, long criticas) {
            this.id = id;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.vulnerabilidades = vulnerabilidades;
            this.criticas = criticas;
        }

        public String getId() {
            return id;
        }

        public String getNombre() {
            return nombre;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public long getVulnerabilidades() {
            return vulnerabilidades;
        }

        public long getCriticas() {
            return criticas;
        }
    }
}
