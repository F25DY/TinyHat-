package com.hexora.hexora.informes.snapshot;

import com.hexora.hexora.informes.TipoInforme;
import java.util.Locale;

/**
 * ID del documento (§6.3): {@code TH-{anio}-{ORG4}-{PRY4}-{TIPO}-v{n}}, con sufijo {@code -NP} si no está publicado.
 * ORG4 y PRY4 son los cuatro primeros caracteres hexadecimales, en mayúscula, de los UUID de la organización y del
 * proyecto; así dos proyectos de la misma organización nunca comparten ID. El nombre del archivo es
 * {@code {ID}.{ext}} y nunca lleva nombres de cliente ni de proyecto.
 */
public final class GeneradorIdInforme {

    public static final String SUFIJO_NO_PUBLICADO = "-NP";

    private GeneradorIdInforme() {
    }

    public static String generar(
        int anio, String organizacionUuid, String proyectoUuid, TipoInforme tipo, int version, boolean noPublicado
    ) {
        if (anio < 1000 || anio > 9999) {
            throw new IllegalArgumentException("El año debe tener cuatro dígitos");
        }
        if (version < 1) {
            throw new IllegalArgumentException("La versión debe ser positiva");
        }
        return "TH-" + anio + "-" + cuatroHex(organizacionUuid) + "-" + cuatroHex(proyectoUuid)
            + "-" + tipo.name() + "-v" + version + (noPublicado ? SUFIJO_NO_PUBLICADO : "");
    }

    /** Los cuatro primeros caracteres hexadecimales del UUID, en mayúscula. */
    static String cuatroHex(String uuid) {
        return hexInicial(uuid, 4);
    }

    /** Los {@code cantidad} primeros caracteres hexadecimales del UUID (sin guiones), en mayúscula. */
    static String hexInicial(String uuid, int cantidad) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID requerido");
        }
        String limpio = uuid.replace("-", "").toUpperCase(Locale.ROOT);
        if (limpio.length() < cantidad || !limpio.substring(0, cantidad).matches("[0-9A-F]+")) {
            throw new IllegalArgumentException("UUID no válido");
        }
        return limpio.substring(0, cantidad);
    }

    /**
     * Código del hallazgo: la aplicación no tiene un código legible, así que se usa {@code H-} más los seis primeros
     * caracteres hexadecimales en mayúscula del UUID de la vulnerabilidad. Es estable entre informes.
     */
    public static String codigoHallazgo(String vulnerabilidadUuid) {
        return "H-" + hexInicial(vulnerabilidadUuid, 6);
    }
}
