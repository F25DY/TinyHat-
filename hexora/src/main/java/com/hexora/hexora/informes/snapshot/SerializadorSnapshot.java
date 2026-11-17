package com.hexora.hexora.informes.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialización canónica del snapshot: el mismo contenido produce siempre los mismos bytes y, por tanto, el mismo
 * SHA-256. Claves de objetos y de mapas ordenadas alfabéticamente, sin sangría, sin campos nulos y con un
 * {@link JsonMapper} propio que no depende de la configuración global de la aplicación.
 */
public final class SerializadorSnapshot {

    private static final JsonMapper MAPPER = JsonMapper.builder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        // Sin esto los records salen en el orden del constructor y no en el alfabético que fija el plan.
        .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .disable(SerializationFeature.INDENT_OUTPUT)
        .changeDefaultPropertyInclusion(incluir -> JsonInclude.Value.construct(
            JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
        .build();

    private SerializadorSnapshot() {
    }

    /** JSON canónico del snapshot. */
    public static String serializar(InformeSnapshot snapshot) {
        return MAPPER.writeValueAsString(snapshot);
    }

    /** SHA-256 en hexadecimal minúscula de los bytes UTF-8 del texto dado. */
    public static String sha256(String texto) {
        try {
            byte[] huella = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(huella);
        } catch (NoSuchAlgorithmException excepcion) {
            throw new IllegalStateException("SHA-256 no está disponible", excepcion);
        }
    }

    /** Huella del snapshot: SHA-256 de su JSON canónico. */
    public static String huella(InformeSnapshot snapshot) {
        return sha256(serializar(snapshot));
    }

    /** Lee un snapshot guardado (para volver a renderizar una versión publicada). */
    public static InformeSnapshot leer(String json) {
        return MAPPER.readValue(json, InformeSnapshot.class);
    }
}
