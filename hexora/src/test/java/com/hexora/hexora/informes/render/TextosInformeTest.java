package com.hexora.hexora.informes.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Los textos fijos de §6.4, palabra por palabra. */
class TextosInformeTest {

    private final TextosInforme textos = new TextosInforme();

    @Test
    void losAvisosSonLosDelPlan() {
        assertThat(textos.aviso("referencia")).isEqualTo(
            "La versión de referencia de este informe es el PDF publicado en TinyHat. Las copias en DOCX o XLSX pueden "
                + "haber sido modificadas después de su descarga; verifique el SHA-256 en la plataforma.");
        assertThat(textos.aviso("alcance")).isEqualTo(
            "Este informe refleja el estado del proyecto en la fecha de emisión indicada. No constituye un dictamen "
                + "legal ni una certificación de seguridad.");
        assertThat(textos.aviso("no-publicado"))
            .isEqualTo("DOCUMENTO NO PUBLICADO — copia de trabajo sin validez como entregable.");
        assertThat(textos.aviso("fase-sugerida")).isEqualTo(
            "Las fases de remediación son una sugerencia derivada de la severidad; los plazos definitivos los acuerda "
                + "el cliente.");
        assertThat(textos.aviso("orden-mr")).isEqualTo(
            "*Nota.* El registro se ordena por nivel de riesgo (probabilidad × impacto) y no solo por severidad técnica. "
                + "Una vulnerabilidad de severidad Crítica con baja probabilidad puede aparecer después de una de severidad "
                + "Alta con probabilidad casi segura. La columna Severidad conserva la clasificación técnica; para "
                + "priorizar por severidad, consulte el plan de mitigación o use el filtro de la hoja.");
    }

    @Test
    void elRecuadroDelAnexoASustituyeLosConteos() {
        assertThat(textos.avisoAnexoA(3, 1)).isEqualTo(
            "**Importante:** este proyecto incluye 3 hallazgo(s) con riesgo aceptado y 1 falso(s) positivo(s). No se "
                + "incluyen en las métricas principales; su detalle y justificación están en el Anexo A.");
        assertThat(textos.avisoAnexoA(0, 0)).contains("0 hallazgo(s)").contains("0 falso(s)");
    }

    @Test
    void lasFrasesDeLasDimensionesCiaSonDePlantilla() {
        assertThat(textos.aviso("cia-c")).isEqualTo("Podría quedar expuesta información que debe mantenerse reservada.");
        assertThat(textos.aviso("cia-i")).isEqualTo("Podría alterarse información o sistemas sin autorización.");
        assertThat(textos.aviso("cia-a")).isEqualTo("Podría interrumpirse el servicio o el acceso a la información.");
    }

    @Test
    void laEscalaYElGlosarioSeCargan() {
        assertThat(textos.escalaCalificacion()).contains("Cómo se califican los hallazgos").contains("NIST SP 800-30 Rev. 1")
            .contains("regla del punto más alto de FIPS 199").contains("| Valor registrado | Nivel | Significado |");
        assertThat(textos.glosario()).contains("| Término | Definición |").contains("CVE").contains("SHA-256")
            .contains("Anexo A");
    }

    @Test
    void unaClaveDesconocidaEsUnError() {
        assertThatThrownBy(() -> textos.aviso("no-existe")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losComentariosDelArchivoNoSonTextos() {
        assertThat(textos.aviso("referencia")).doesNotContain("Textos fijos").doesNotContain("#");
    }
}
