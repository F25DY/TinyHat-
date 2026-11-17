package com.hexora.hexora.informes.snapshot;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.Entrada;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteArea;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteCve;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.EntradaTrazabilidad;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Datos ficticios para las pruebas del snapshot. Nada de esto es real. */
final class DatosDePrueba {

    static final Instant AHORA = Instant.parse("2026-11-20T15:15:00Z");
    static final String ORG_UUID = "7f3a1c00-0000-4000-8000-0000000000aa";
    static final String PRY_UUID = "0c91e200-0000-4000-8000-0000000000bb";
    static final FuenteArea RED = new FuenteArea("area-red", "Red", "Infraestructura de red");
    static final FuenteArea APLICACIONES = new FuenteArea("area-app", "Aplicaciones", "Aplicaciones web");
    static final FuenteArea VACIA = new FuenteArea("area-vacia", "Área sin hallazgos", null);

    private DatosDePrueba() {
    }

    /** UUID con los primeros seis caracteres hexadecimales dados (para predecir el código H-XXXXXX). */
    static String uuid(String seisHex) {
        return seisHex + "00-0000-4000-8000-000000000001";
    }

    static FuenteVulnerabilidad vulnerabilidad(
        String seisHex, FuenteArea area, String titulo, int c, int i, int a, Integer probabilidad, String estado
    ) {
        return new FuenteVulnerabilidad(uuid(seisHex), area.clave(), titulo, c, i, a, probabilidad, estado,
            "Aplicar el parche del proveedor", null, null);
    }

    static FuenteVulnerabilidad conCve(FuenteVulnerabilidad base, String codigo, FuenteCve cache) {
        return new FuenteVulnerabilidad(base.publicId(), base.areaClave(), base.titulo(), base.confidencialidad(),
            base.integridad(), base.disponibilidad(), base.probabilidad0a10(), base.estado(), base.mitigacion(),
            codigo, cache);
    }

    static FuenteVulnerabilidad sinMitigacion(FuenteVulnerabilidad base) {
        return new FuenteVulnerabilidad(base.publicId(), base.areaClave(), base.titulo(), base.confidencialidad(),
            base.integridad(), base.disponibilidad(), base.probabilidad0a10(), base.estado(), "   ",
            base.cveCodigo(), base.cve());
    }

    static FuenteCve cveEnCache() {
        return new FuenteCve(new BigDecimal("9.8"), LocalDateTime.of(2024, 1, 10, 3, 0),
            "https://vendor.example/parche-1\nftp://no-permitido\nhttps://vendor.example/parche-2\n\nhttps://vendor.example/parche-1");
    }

    static Entrada entrada(TipoInforme tipo, List<FuenteArea> areas, List<FuenteVulnerabilidad> vulnerabilidades) {
        return new Entrada(tipo, 2, "PUBLICADO", false, 2026, ORG_UUID, PRY_UUID, "Organización Ficticia S.A.S.",
            "Proyecto Piloto", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 16), "Auditores Demo",
            areas, vulnerabilidades, Map.of("resumenEjecutivo", "Resumen **ficticio**."), List.of(), AHORA, "2026.1",
            1000, ReferenciasApa.desdeClasspath());
    }

    /** Entrada con dos áreas y un conjunto variado de hallazgos. */
    static Entrada entradaVariada(TipoInforme tipo) {
        return entrada(tipo, List.of(APLICACIONES, RED, VACIA), List.of(
            vulnerabilidad("aaaaaa", RED, "Puerto expuesto", 9, 2, 1, 8, "Abierta"),
            vulnerabilidad("bbbbbb", RED, "Cifrado débil", 5, 5, 5, 5, "En Progreso"),
            vulnerabilidad("cccccc", APLICACIONES, "Inyección SQL", 10, 10, 10, 10, "Abierta"),
            vulnerabilidad("dddddd", APLICACIONES, "Cabecera ausente", 1, 0, 0, 1, "Mitigada"),
            vulnerabilidad("eeeeee", APLICACIONES, "Riesgo asumido", 8, 8, 8, 9, "Aceptada")));
    }

    static List<EntradaTrazabilidad> trazabilidad() {
        return List.of(new EntradaTrazabilidad("TH-2026-7F3A-0C91-EJE-v1", "EJE", "PDF", "a".repeat(64),
            "2026-11-18T09:00:00-05:00"));
    }
}
