package com.hexora.hexora.informes;

/** Formatos de salida de un informe. */
public enum FormatoInforme {

    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String mime;
    private final String extension;

    FormatoInforme(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    /** Tipo MIME exacto con el que se descarga. */
    public String mime() {
        return mime;
    }

    /** Extensión del archivo, sin punto. */
    public String extension() {
        return extension;
    }
}
