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

    @ParameterizedTest
    @CsvSource({
        "-3,1", "0,1", "1,1", "2,2", "3,2", "4,3", "5,3", "6,4", "7,4", "8,5", "9,5", "10,5", "99,5"
    })
    void convierteLaEscalaCeroADiezABandasUnoACinco(int valor, int esperado) {
        assertThat(EscalaRiesgo.banda(valor)).isEqualTo(esperado);
        assertThat(EscalaRiesgo.probabilidad(valor)).isEqualTo(esperado);
    }

    @Test
    void elImpactoEsLaDimensionCiaMasAlta() {
        assertThat(EscalaRiesgo.impacto(0, 0, 0)).isEqualTo(1);
        assertThat(EscalaRiesgo.impacto(2, 9, 1)).isEqualTo(5);
        assertThat(EscalaRiesgo.impacto(4, 3, 5)).isEqualTo(3);
        assertThat(EscalaRiesgo.impacto(10, 10, 10)).isEqualTo(5);
    }

    @Test
    void lasEtiquetasDeProbabilidadSonLasDelPlan() {
        assertThat(EscalaRiesgo.etiquetaProbabilidad(1)).isEqualTo("Rara");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(2)).isEqualTo("Improbable");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(3)).isEqualTo("Posible");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(4)).isEqualTo("Probable");
        assertThat(EscalaRiesgo.etiquetaProbabilidad(5)).isEqualTo("Casi segura");
    }

    /** Los 25 casos de la tabla de búsqueda de §4.2 (filas: probabilidad 5..1; columnas: impacto 1..5). */
    @ParameterizedTest
    @CsvSource({
        "5,1,MEDIO", "5,2,ALTO", "5,3,CRITICO", "5,4,CRITICO", "5,5,CRITICO",
        "4,1,BAJO", "4,2,MEDIO", "4,3,ALTO", "4,4,CRITICO", "4,5,CRITICO",
        "3,1,BAJO", "3,2,MEDIO", "3,3,MEDIO", "3,4,ALTO", "3,5,CRITICO",
        "2,1,BAJO", "2,2,BAJO", "2,3,MEDIO", "2,4,MEDIO", "2,5,ALTO",
        "1,1,BAJO", "1,2,BAJO", "1,3,BAJO", "1,4,MEDIO", "1,5,MEDIO"
    })
    void laTablaDeNivelesCoincideConElPlan(int probabilidad, int impacto, NivelRiesgo esperado) {
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
    void elNivelCriticoSeImprimeConTilde() {
        assertThat(NivelRiesgo.CRITICO.etiqueta()).isEqualTo("Crítico");
    }

    @Test
    void elNivelDeUnaVulnerabilidadSaleDeLaDimensionCiaMasAltaYLaProbabilidad() {
        // Una sola dimensión alta basta: la media (3,33) la habría dejado en Medio o menos.
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(9, 0, 0, 8)).isEqualTo(NivelRiesgo.CRITICO);
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(0, 0, 10, 10)).isEqualTo(NivelRiesgo.CRITICO);
        assertThat(EscalaRiesgo.nivelDeVulnerabilidad(1, 0, 1, 1)).isEqualTo(NivelRiesgo.BAJO);
        assertThat(EscalaRiesgo.impactoGuardado(3, 7, 5)).isEqualTo(7);
        assertThat(EscalaRiesgo.impactoGuardado(-4, 99, 5)).isEqualTo(10);
    }

    @Test
    void elValorAlmacenadoNoLlevaTildeYLaEtiquetaSi() {
        assertThat(NivelRiesgo.CRITICO.valorAlmacenado()).isEqualTo("Critico");
        assertThat(NivelRiesgo.BAJO.valorAlmacenado()).isEqualTo("Bajo");
    }

    /** El formulario calcula el nivel en el navegador: su tabla debe ser la misma que la de Java. */
    @Test
    void laTablaDelFormularioJavaScriptCoincideConLaDeJava() throws Exception {
        String js = Files.readString(
            Path.of("src/main/resources/static/js/vulnerability-form.js"), StandardCharsets.UTF_8);
        Matcher bloque = Pattern.compile("const NIVELES = \\[(.*?)\\n\\];", Pattern.DOTALL).matcher(js);
        assertThat(bloque.find()).isTrue();
        Matcher filas = Pattern.compile("\\[([^\\]]*)\\]").matcher(bloque.group(1));
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
}
