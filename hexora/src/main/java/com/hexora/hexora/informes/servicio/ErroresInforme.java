package com.hexora.hexora.informes.servicio;

/**
 * Errores del ciclo de vida de los informes. Los mensajes son de negocio o genéricos: nunca llevan rutas, hashes,
 * contenido de la narrativa ni de los hallazgos. La capa web decide el código HTTP (Etapa 7): {@link Ocupado} 503,
 * {@link Conflicto} 409, {@link Integridad} y {@link Publicacion} 500 genérico.
 */
public final class ErroresInforme {

    private ErroresInforme() {
    }

    /** No hay cupo de generación en este momento (semáforo lleno): "intente en un momento". */
    public static final class Ocupado extends RuntimeException {

        public Ocupado() {
            super("El sistema está generando otros informes. Intente de nuevo en un momento.");
        }
    }

    /** La operación no es válida para el estado actual (otro borrador, otra versión vigente, versión no editable...). */
    public static final class Conflicto extends RuntimeException {

        public Conflicto(String mensaje) {
            super(mensaje);
        }
    }

    /** El archivo en disco no coincide con el hash registrado: no se sirve. */
    public static final class Integridad extends RuntimeException {

        public Integridad() {
            super("No fue posible entregar el archivo.");
        }
    }

    /** Fallo técnico al generar o publicar; el borrador queda intacto. */
    public static final class Publicacion extends RuntimeException {

        public Publicacion(Throwable causa) {
            super("No fue posible completar la operación del informe.", causa);
        }
    }
}
