package com.hexora.hexora.dto;

/** Cantidad de hallazgos con un nivel de riesgo y un estado (resultado de una consulta agrupada). */
public record ConteoHallazgos(String nivelRiesgo, String estado, Long total) {
}
