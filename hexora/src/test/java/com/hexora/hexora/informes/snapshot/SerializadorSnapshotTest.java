package com.hexora.hexora.informes.snapshot;

import static com.hexora.hexora.informes.snapshot.DatosDePrueba.entradaVariada;
import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.Entrada;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Serialización canónica: mismo contenido, mismos bytes, misma huella. */
class SerializadorSnapshotTest {

    private static InformeSnapshot snapshot() {
        return ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));
    }

    @Test
    void elMismoSnapshotDaElMismoJsonYElMismoHash() {
        String a = SerializadorSnapshot.serializar(snapshot());
        String b = SerializadorSnapshot.serializar(snapshot());
        assertThat(a).isEqualTo(b);
        assertThat(SerializadorSnapshot.huella(snapshot())).isEqualTo(SerializadorSnapshot.huella(snapshot()))
            .matches("[0-9a-f]{64}");
    }

    @Test
    void elOrdenDeInsercionDeLosMapasNoCambiaElHash() {
        Entrada base = entradaVariada(TipoInforme.TEC);
        Map<String, String> adelante = new LinkedHashMap<>();
        adelante.put("alcanceMetodologia", "Uno");
        adelante.put("conclusiones", "Dos");
        adelante.put("resumenEjecutivo", "Tres");
        Map<String, String> atras = new LinkedHashMap<>();
        atras.put("resumenEjecutivo", "Tres");
        atras.put("conclusiones", "Dos");
        atras.put("alcanceMetodologia", "Uno");

        String h1 = SerializadorSnapshot.huella(ConstructorSnapshot.construir(conNarrativa(base, adelante)));
        String h2 = SerializadorSnapshot.huella(ConstructorSnapshot.construir(conNarrativa(base, atras)));
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void unCambioMinimoCambiaElHash() {
        Entrada base = entradaVariada(TipoInforme.TEC);
        String original = SerializadorSnapshot.huella(ConstructorSnapshot.construir(base));
        String cambiado = SerializadorSnapshot.huella(
            ConstructorSnapshot.construir(conNarrativa(base, Map.of("resumenEjecutivo", "Resumen **ficticio**!"))));
        assertThat(cambiado).isNotEqualTo(original);
    }

    @Test
    void lasClavesSalenOrdenadasAlfabeticamenteYSinCamposNulos() {
        String json = SerializadorSnapshot.serializar(snapshot());
        // Claves de primer nivel en orden alfabético.
        List<String> claves = List.of("anexoA", "areas", "auditor", "generadoEn", "informe", "metricas", "narrativa",
            "organizacion", "plantillaVersion", "proyecto", "referenciasApa", "schemaVersion", "trazabilidad");
        int anterior = -1;
        for (String clave : claves) {
            int posicion = json.indexOf("\"" + clave + "\":");
            assertThat(posicion).as(clave).isGreaterThan(anterior);
            anterior = posicion;
        }
        assertThat(json).doesNotContain("null").doesNotContain("\n");
        // Un hallazgo cuyo CVE no existe no escribe campos vacíos.
        assertThat(json).doesNotContain("\"cves\":null");
    }

    @Test
    void lasFechasVanEnIsoConZonaBogota() {
        String json = SerializadorSnapshot.serializar(snapshot());
        assertThat(json).contains("\"generadoEn\":\"2026-11-20T10:15:00-05:00\"")
            .contains("\"fechaInicio\":\"2026-11-02\"");
    }

    @Test
    void sePuedeLeerLoGuardadoYVuelveAserIgual() {
        InformeSnapshot original = snapshot();
        InformeSnapshot leido = SerializadorSnapshot.leer(SerializadorSnapshot.serializar(original));
        assertThat(SerializadorSnapshot.huella(leido)).isEqualTo(SerializadorSnapshot.huella(original));
        assertThat(leido.informe()).isEqualTo(original.informe());
        assertThat(leido.areas()).isEqualTo(original.areas());
        assertThat(leido.metricas()).isEqualTo(original.metricas());
    }

    @Test
    void laHuellaEsElSha256DeLosBytesUtf8() {
        // Vector conocido de SHA-256.
        assertThat(SerializadorSnapshot.sha256("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        // Los acentos se codifican en UTF-8 (no en la codificación de la plataforma).
        assertThat(SerializadorSnapshot.sha256("ñ✓")).isEqualTo(SerializadorSnapshot.sha256("ñ✓"))
            .isNotEqualTo(SerializadorSnapshot.sha256("n"));
        assertThat(SerializadorSnapshot.serializar(snapshot())).contains("Organización Ficticia S.A.S.");
    }

    private static Entrada conNarrativa(Entrada b, Map<String, String> narrativa) {
        return new Entrada(b.tipo(), b.numeroVersion(), b.estadoDocumento(), b.noPublicado(), b.anio(),
            b.organizacionUuid(), b.proyectoUuid(), b.organizacionNombre(), b.proyectoNombre(), b.fechaInicio(),
            b.fechaFin(), b.auditorNombre(), b.areas(), b.vulnerabilidades(), narrativa, b.trazabilidad(),
            b.generadoEn(), b.plantillaVersion(), b.maxHallazgos(), b.referencias());
    }
}
