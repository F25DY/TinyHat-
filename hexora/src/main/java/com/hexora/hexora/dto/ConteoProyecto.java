package com.hexora.hexora.dto;

/** Cantidad de hallazgos de un proyecto con un nivel de riesgo y un estado (consulta agrupada, sin N+1). */
public record ConteoProyecto(Long proyectoId, String nivelRiesgo, String estado, Long total) {
}
