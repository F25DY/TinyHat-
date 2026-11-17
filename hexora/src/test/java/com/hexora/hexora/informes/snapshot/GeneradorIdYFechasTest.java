package com.hexora.hexora.informes.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.TipoInforme;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GeneradorIdYFechasTest {

    @Test
    void elIdSigueElFormatoDelPlan() {
        assertThat(GeneradorIdInforme.generar(2026, "7f3a1c00-0000-4000-8000-0000000000aa",
            "0c91e200-0000-4000-8000-0000000000bb", TipoInforme.TEC, 2, false))
            .isEqualTo("TH-2026-7F3A-0C91-TEC-v2");
    }

    @Test
    void unaCopiaNoPublicadaLlevaElSufijoNp() {
        assertThat(GeneradorIdInforme.generar(2026, "7f3a1c00-0000-4000-8000-0000000000aa",
            "0c91e200-0000-4000-8000-0000000000bb", TipoInforme.EJE, 1, true))
            .isEqualTo("TH-2026-7F3A-0C91-EJE-v1-NP");
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void cadaTipoUsaSuCodigo(TipoInforme tipo) {
        assertThat(GeneradorIdInforme.generar(2026, UUID.randomUUID().toString(), UUID.randomUUID().toString(), tipo,
            3, false)).matches("TH-2026-[0-9A-F]{4}-[0-9A-F]{4}-" + tipo.name() + "-v3");
    }

    @Test
    void dosProyectosDeLaMismaOrganizacionNoComparteIdYElIdNoLlevaNombres() {
        String org = "7f3a1c00-0000-4000-8000-0000000000aa";
        String a = GeneradorIdInforme.generar(2026, org, "0c91e200-0000-4000-8000-0000000000bb", TipoInforme.TEC, 1, false);
        String b = GeneradorIdInforme.generar(2026, org, "9d02e200-0000-4000-8000-0000000000cc", TipoInforme.TEC, 1, false);
        assertThat(a).isNotEqualTo(b);
        assertThat(a).matches("TH-\\d{4}-[0-9A-F]{4}-[0-9A-F]{4}-[A-Z]{2,3}-v\\d+");
    }

    @Test
    void rechazaEntradasInvalidas() {
        String u = UUID.randomUUID().toString();
        assertThatThrownBy(() -> GeneradorIdInforme.generar(26, u, u, TipoInforme.MR, 1, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeneradorIdInforme.generar(2026, u, u, TipoInforme.MR, 0, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeneradorIdInforme.generar(2026, "zzzz", u, TipoInforme.MR, 1, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeneradorIdInforme.generar(2026, null, u, TipoInforme.MR, 1, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elCodigoDelHallazgoSonSeisHexEnMayuscula() {
        assertThat(GeneradorIdInforme.codigoHallazgo("3f9a1c22-1111-4222-8333-444444444444")).isEqualTo("H-3F9A1C");
        assertThat(GeneradorIdInforme.codigoHallazgo(UUID.randomUUID().toString())).matches("H-[0-9A-F]{6}");
        assertThatThrownBy(() -> GeneradorIdInforme.codigoHallazgo("no-es-uuid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- fechas

    @Test
    void laFechaIsoUsaLaZonaDeBogota() {
        assertThat(FormatoFechas.iso(Instant.parse("2026-11-20T15:15:00Z"))).isEqualTo("2026-11-20T10:15:00-05:00");
        assertThat(FormatoFechas.iso(Instant.parse("2026-11-20T15:15:00.987Z"))).as("sin fracciones")
            .isEqualTo("2026-11-20T10:15:00-05:00");
        assertThat(FormatoFechas.isoDesdeUtc(LocalDateTime.of(2026, 11, 20, 15, 15, 0)))
            .isEqualTo("2026-11-20T10:15:00-05:00");
    }

    @Test
    void laFechaLargaEsEnEspanolDeColombia() {
        assertThat(FormatoFechas.fechaLarga(LocalDate.of(2026, 11, 16))).isEqualTo("16 de noviembre de 2026");
        assertThat(FormatoFechas.fechaLarga(LocalDate.of(2026, 3, 1))).isEqualTo("1 de marzo de 2026");
        assertThat(FormatoFechas.fechaLarga("2026-11-20T10:15:00-05:00")).isEqualTo("20 de noviembre de 2026");
    }

    @Test
    void laFechaLargaDeUnInstanteCercanoAMedianocheUsaLaHoraDeBogota() {
        // 2026-11-21 02:30 UTC todavía es 20 de noviembre a las 21:30 en Bogotá.
        String iso = FormatoFechas.iso(Instant.parse("2026-11-21T02:30:00Z"));
        assertThat(FormatoFechas.fechaLarga(iso)).isEqualTo("20 de noviembre de 2026");
    }

    @Test
    void elAnioDeLaSerieEsElDeBogota() {
        assertThat(FormatoFechas.anio(Instant.parse("2027-01-01T03:00:00Z"))).as("aún es 2026 en Bogotá")
            .isEqualTo((short) 2026);
        assertThat(FormatoFechas.anio(Instant.parse("2027-01-01T05:00:00Z"))).isEqualTo((short) 2027);
    }

    @Test
    void laFechaIsoDeUnDiaNuloEsNula() {
        assertThat(FormatoFechas.isoFecha(null)).isNull();
        assertThat(FormatoFechas.isoFecha(LocalDate.of(2026, 11, 2))).isEqualTo("2026-11-02");
    }
}
