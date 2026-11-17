package com.hexora.hexora.riesgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.riesgo.EscalaRiesgo.NivelRiesgo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EscalaRiesgoTest {

    /** Cortes de NIST SP 800-30 Rev. 1 (0-4, 5-20, 21-79, 80-95, 96-100) escalados a valores enteros 0-10. */
    @ParameterizedTest
    @CsvSource({
        "-3,1", "0,1", "1,2", "2,2", "3,3", "4,3", "5,3", "6,3", "7,3", "8,4", "9,4", "10,5", "99,5"
    })
    void convierteLaEscalaCeroADiezAlosCincoNivelesDeNist(int valor, int esperado) {
        assertThat(EscalaRiesgo.banda(valor)).isEqualTo(esperado);
        assertThat(EscalaRiesgo.probabilidad(valor)).isEqualTo(esperado);
    }

    @Test
    void elImpactoEsLaDimensionCiaMasAltaReglaDelPuntoMasAlto() {
        assertThat(EscalaRiesgo.impacto(0, 0, 0)).isEqualTo(1);
        assertThat(EscalaRiesgo.impacto(2, 9, 1)).isEqualTo(4);
        assertThat(EscalaRiesgo.impacto(4, 3, 5)).isEqualTo(3);
        assertThat(EscalaRiesgo.impacto(10, 10, 10)).isEqualTo(5);
    }

    @Test
    void lasEtiquetasDeProbabilidadSonLasDeNist() {
        assertThat(EscalaRiesgo.etiquetaProbabilidad(1)).isEqualTo("Muy baja");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(2)).isEqualTo("Baja");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(3)).isEqualTo("Media");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(4)).isEqualTo("Alta");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(5)).isEqualTo("Muy alta");
    }

    /**
     * NIST SP 800-30 Rev. 1, Tabla I-2, copiada del PDF oficial (p. I-1). Probabilidad 5..1 (Muy alta a Muy baja)
     * por impacto 1..5. MUY_BAJO = Very Low, BAJO = Low, MEDIO = Moderate, ALTO = High, CRITICO = Very High.
     */
    @ParameterizedTest
    @CsvSource({
        "5,1,MUY_BAJO", "5,2,BAJO", "5,3,MEDIO", "5,4,ALTO", "5,5,CRITICO",
        "4,1,MUY_BAJO", "4,2,BAJO", "4,3,MEDIO", "4,4,ALTO", "4,5,CRITICO",
        "3,1,MUY_BAJO", "3,2,BAJO", "3,3,MEDIO", "3,4,MEDIO", "3,5,ALTO",
        "2,1,MUY_BAJO", "2,2,BAJO", "2,3,BAJO", "2,4,BAJO", "2,5,MEDIO",
        "1,1,MUY_BAJO", "1,2,MUY_BAJO", "1,3,MUY_BAJO", "1,4,BAJO", "1,5,BAJO"
    })
    void laTablaDeNivelesEsLaTablaI2DeNist(int probabilidad, int impacto, NivelRiesgo esperado) {
        assertThat(EscalaRiesgo.nivel(probabilidad, impacto)).isEqualTo(esperado);
        assertThat(EscalaRiesgo.puntaje(probabilidad, impacto)).isEqualTo(probabilidad * impacto);
    }

    @Test
    void rechazaValoresFueraDeLaEscala() {
        assertThatThrownBy(() -> EscalaRiesgo.nivel(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EscalaRiesgo.nivel(3, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EscalaRiesgo.etiquetaProbabilidad(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elNivelDeUnaVulnerabilidadSaleDeLaDimensionCiaMasAltaYLaProbabilidad() {
        // Una sola dimensión alta basta: la media (3,33) la habría dejado muy abajo.
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(9, 0, 0, 8)).isEqualTo(NivelRiesgo.ALTO);
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(0, 0, 10, 10)).isEqualTo(NivelRiesgo.CRITICO);
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(1, 0, 1, 1)).isEqualTo(NivelRiesgo.BAJO);
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(0, 0, 0, 10)).isEqualTo(NivelRiesgo.MUY_BAJO);
        // 5/5/5 con probabilidad 5 es "Medio" en la matriz de NIST (nivel 3 por 3).
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(5, 5, 5, 5)).isEqualTo(NivelRiesgo.MEDIO);
        assertThat(EscalaRiesgo.impactoGuardado(3, 7, 5)).isEqualTo(7);
        assertThat(EscalaRiesgo.impactoGuardado(-4, 99, 5)).isEqualTo(10);
    }

    @Test
    void losNivelesTienenEtiquetaValorGuardadoYOrden() {
        assertThat(NivelRiesgo.CRITICO.etiqueta()).isEqualTo("Crítico");
        assertThat(NivelRiesgo.CRITICO.valorAlmacenado()).isEqualTo("Critico");
        assertThat(NivelRiesgo.MUY_BAJO.etiqueta()).isEqualTo("Muy bajo");
        assertThat(NivelRiesgo.MUY_BAJO.valorAlmacenado()).isEqualTo("Muy bajo");
        assertThat(NivelRiesgo.deMayorAMenor())
            .containsExactly(NivelRiesgo.CRITICO, NivelRiesgo.ALTO, NivelRiesgo.MEDIO, NivelRiesgo.BAJO,
                NivelRiesgo.MUY_BAJO);
        assertThat(NivelRiesgo.CRITICO.posicionDeCriticidad()).isZero();
        assertThat(NivelRiesgo.MUY_BAJO.posicionDeCriticidad()).isEqualTo(4);
    }

    @Test
    void interpretaElValorGuardadoSinImportarTildesNiMayusculas() {
        assertThat(NivelRiesgo.desdeAlmacenado("Critico")).isEqualTo(NivelRiesgo.CRITICO);
        assertThat(NivelRiesgo.desdeAlmacenado("Crítico")).isEqualTo(NivelRiesgo.CRITICO);
        assertThat(NivelRiesgo.desdeAlmacenado(" muy BAJO ")).isEqualTo(NivelRiesgo.MUY_BAJO);
        assertThat(NivelRiesgo.desdeAlmacenado("inexistente")).isNull();
        assertThat(NivelRiesgo.desdeAlmacenado(null)).isNull();
    }

    /** El formulario calcula el nivel en el navegador: su tabla debe ser la misma que la de Java. */
    @Test
    void laTablaDelFormularioJavaScriptCoincideConLaDeJava() throws Exception {
        String js = Files.readString(
            Path.of("src/main/resources/static/js/vulnerability-form.js"), StandardCharsets.UTF_8);
        Matcher bloque = Pattern.compile("const NIVELES = \\[(.*?)\\n\\];", Pattern.DOTALL).matcher(js);
        assertThat(bloque.find()).isTrue();
        Matcher filas = Pattern.compile("^\\s*\\[([^\\]]*)\\]", Pattern.MULTILINE).matcher(bloque.group(1));
        int probabilidad = 0;
        while (filas.find()) {
            probabilidad++;
            String[] niveles = filas.group(1).replace("\"", "").split(",");
            assertThat(niveles).hasSize(5);
            for (int impacto = 1; impacto <= 5; impacto++) {
                assertThat(niveles[impacto - 1].strip())
                    .as("P" + probabilidad + " I" + impacto)
                    .isEqualTo(EscalaRiesgo.nivel(probabilidad, impacto).valorAlmacenado());
            }
        }
        assertThat(probabilidad).isEqualTo(5);
    }

    /** Los cortes de NIST también están en el JS del formulario. */
    @Test
    void losCortesDelFormularioJavaScriptCoincidenConLosDeJava() throws Exception {
        String js = Files.readString(
            Path.of("src/main/resources/static/js/vulnerability-form.js"), StandardCharsets.UTF_8);
        assertThat(js).contains("if (acotado === 0) return 1;")
            .contains("if (acotado <= 2) return 2;")
            .contains("if (acotado <= 7) return 3;")
            .contains("return acotado <= 9 ? 4 : 5;");
    }
}
