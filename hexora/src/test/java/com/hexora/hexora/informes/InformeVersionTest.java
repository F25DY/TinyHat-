package com.hexora.hexora.informes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.model.InformeVersion;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Invariantes I3 e I5 del ciclo de vida, sin base de datos. */
class InformeVersionTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final String SHA = "a".repeat(64);

    private static InformeVersion borrador() {
        return new InformeVersion(1L, 1, 7L, "{\"resumenEjecutivo\":\"Texto\"}");
    }

    private static InformeVersion publicada() {
        InformeVersion version = borrador();
        version.publicar("{\"schemaVersion\":1}", SHA, "2026.1", AHORA);
        return version;
    }

    @ParameterizedTest
    @CsvSource({
        "BORRADOR,BORRADOR,false", "BORRADOR,PUBLICADO,true", "BORRADOR,DESPUBLICADO,false",
        "PUBLICADO,BORRADOR,false", "PUBLICADO,PUBLICADO,false", "PUBLICADO,DESPUBLICADO,true",
        "DESPUBLICADO,BORRADOR,false", "DESPUBLICADO,PUBLICADO,false", "DESPUBLICADO,DESPUBLICADO,false"
    })
    void soloSeAdmitenDosTransiciones(EstadoVersion origen, EstadoVersion destino, boolean valida) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(valida);
    }

    @Test
    void soloUnBorradorSeElimina() {
        assertThat(EstadoVersion.BORRADOR.permiteEliminar()).isTrue();
        assertThat(EstadoVersion.PUBLICADO.permiteEliminar()).isFalse();
        assertThat(EstadoVersion.DESPUBLICADO.permiteEliminar()).isFalse();
    }

    @Test
    void soloLoPublicadoLoVeElCliente() {
        assertThat(EstadoVersion.BORRADOR.visibleParaCliente()).isFalse();
        assertThat(EstadoVersion.PUBLICADO.visibleParaCliente()).isTrue();
        assertThat(EstadoVersion.DESPUBLICADO.visibleParaCliente()).isFalse();
    }

    @Test
    void unBorradorNaceSinSnapshotYConLaNarrativaIndicada() {
        InformeVersion version = borrador();
        assertThat(version.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
        assertThat(version.getSnapshotJson()).isNull();
        assertThat(version.getSnapshotSha256()).isNull();
        assertThat(version.getPublicadoEn()).isNull();
        assertThat(version.getNarrativaJson()).contains("resumenEjecutivo");
        assertThat(new InformeVersion(1L, 1, 7L, null).getNarrativaJson()).isEqualTo("{}");
    }

    @Test
    void publicarCongelaElSnapshotYLaFecha() {
        InformeVersion version = publicada();
        assertThat(version.getEstado()).isEqualTo(EstadoVersion.PUBLICADO);
        assertThat(version.getSnapshotJson()).isEqualTo("{\"schemaVersion\":1}");
        assertThat(version.getSnapshotSha256()).isEqualTo(SHA);
        assertThat(version.getPlantillaVersion()).isEqualTo("2026.1");
        assertThat(version.getPublicadoEn()).isEqualTo(AHORA);
        assertThat(version.getDespublicadoEn()).isNull();
    }

    @Test
    void unaVersionPublicadaNoSeVuelveAPublicarNiSeEdita() {
        InformeVersion version = publicada();
        assertThatThrownBy(() -> version.publicar("{}", SHA, "2026.1", AHORA))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> version.actualizarNarrativa("{}", AHORA))
            .isInstanceOf(IllegalStateException.class);
        assertThat(version.getSnapshotJson()).isEqualTo("{\"schemaVersion\":1}");
    }

    @Test
    void publicarExigeSnapshotHashYPlantilla() {
        assertThatThrownBy(() -> borrador().publicar(null, SHA, "2026.1", AHORA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> borrador().publicar("{}", null, "2026.1", AHORA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> borrador().publicar("{}", SHA, null, AHORA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(borrador().getEstado()).isEqualTo(EstadoVersion.BORRADOR);
    }

    @Test
    void unBorradorEsEditable() {
        InformeVersion version = borrador();
        version.actualizarNarrativa("{\"conclusiones\":\"Nuevo\"}", AHORA);
        assertThat(version.getNarrativaJson()).isEqualTo("{\"conclusiones\":\"Nuevo\"}");
        assertThat(version.getActualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    void unBorradorNoSePuedeDespublicar() {
        assertThatThrownBy(() -> borrador().despublicar("Motivo suficientemente largo", AHORA))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void despublicarRegistraMotivoYFechaYNadaMasCambia() {
        InformeVersion version = publicada();
        version.despublicar("  Se detectó un error en la matriz  ", AHORA.plusDays(1));
        assertThat(version.getEstado()).isEqualTo(EstadoVersion.DESPUBLICADO);
        assertThat(version.getMotivoDespublicacion()).isEqualTo("Se detectó un error en la matriz");
        assertThat(version.getDespublicadoEn()).isEqualTo(AHORA.plusDays(1));
        assertThat(version.getSnapshotJson()).isEqualTo("{\"schemaVersion\":1}");
        assertThat(version.getSnapshotSha256()).isEqualTo(SHA);
        assertThat(version.getPublicadoEn()).isEqualTo(AHORA);
    }

    @Test
    void elMotivoDebeTenerEntre10Y500Caracteres() {
        assertThatThrownBy(() -> publicada().despublicar(null, AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicada().despublicar("          ", AHORA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicada().despublicar("corto", AHORA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publicada().despublicar("x".repeat(501), AHORA))
            .isInstanceOf(IllegalArgumentException.class);
        publicada().despublicar("x".repeat(10), AHORA);
        publicada().despublicar("x".repeat(500), AHORA);
    }

    @Test
    void unaVersionDespublicadaNoVuelveAPublicarseNiSeEdita() {
        InformeVersion version = publicada();
        version.despublicar("Motivo suficientemente largo", AHORA);
        assertThatThrownBy(() -> version.publicar("{}", SHA, "2026.1", AHORA))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> version.despublicar("Motivo suficientemente largo", AHORA))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> version.actualizarNarrativa("{}", AHORA))
            .isInstanceOf(IllegalStateException.class);
    }

    /** El callback de JPA que impide borrar lo publicado (I3). */
    @Test
    void laEntidadNoPermiteEliminarLoPublicadoNiLoDespublicado() throws Exception {
        invocarPreRemove(borrador());
        assertThatThrownBy(() -> invocarPreRemove(publicada())).hasCauseInstanceOf(IllegalStateException.class);
        InformeVersion despublicada = publicada();
        despublicada.despublicar("Motivo suficientemente largo", AHORA);
        assertThatThrownBy(() -> invocarPreRemove(despublicada)).hasCauseInstanceOf(IllegalStateException.class);
    }

    private static void invocarPreRemove(InformeVersion version)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method callback = InformeVersion.class.getDeclaredMethod("impedirEliminarLoPublicado");
        callback.setAccessible(true);
        callback.invoke(version);
    }
}
