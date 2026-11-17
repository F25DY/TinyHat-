package com.hexora.hexora.informes.snapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Fechas de los informes: zona America/Bogota; ISO-8601 en el snapshot y es-CO en lo que se imprime. */
public final class FormatoFechas {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES_CO);

    private FormatoFechas() {
    }

    /** ISO-8601 con zona Bogotá y precisión de segundos, por ejemplo {@code 2026-11-20T10:15:00-05:00}. */
    public static String iso(Instant instante) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instante.truncatedTo(ChronoUnit.SECONDS).atZone(BOGOTA));
    }

    /** Igual que {@link #iso(Instant)} para las fechas que la base guarda en UTC sin zona. */
    public static String isoDesdeUtc(LocalDateTime utc) {
        return iso(utc.toInstant(ZoneOffset.UTC));
    }

    /** Solo la fecha, ISO-8601. */
    public static String isoFecha(LocalDate fecha) {
        return fecha == null ? null : DateTimeFormatter.ISO_LOCAL_DATE.format(fecha);
    }

    /** "16 de noviembre de 2026". */
    public static String fechaLarga(LocalDate fecha) {
        return FECHA_LARGA.format(fecha);
    }

    /** Fecha larga de un instante ISO-8601 con zona, en hora de Bogotá. */
    public static String fechaLarga(String isoConZona) {
        return fechaLarga(java.time.OffsetDateTime.parse(isoConZona).atZoneSameInstant(BOGOTA).toLocalDate());
    }

    /** Año de creación de una serie, en hora de Bogotá. */
    public static short anio(Instant instante) {
        return (short) instante.atZone(BOGOTA).getYear();
    }
}
