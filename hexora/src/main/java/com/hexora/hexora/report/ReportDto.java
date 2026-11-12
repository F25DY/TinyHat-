package com.hexora.hexora.report;

import com.hexora.hexora.model.Informe;

public record ReportDto(
    String publicId,
    String projectPublicId,
    String tipo,
    String titulo,
    String audiencia,
    String formato,
    String estado,
    String creadoEn,
    String publicadoEn,
    String hashContenido
) {

    static ReportDto from(Informe report, String projectPublicId) {
        return new ReportDto(
            report.getPublicId(),
            projectPublicId,
            report.getTipo(),
            report.getTitulo(),
            report.getAudiencia(),
            report.getFormato(),
            report.getEstado(),
            report.getCreadoEn() == null ? null : report.getCreadoEn().toString() + "Z",
            report.getPublicadoEn() == null ? null : report.getPublicadoEn().toString() + "Z",
            report.getHashContenido());
    }
}
