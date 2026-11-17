package com.hexora.hexora.informes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TipoInformeTest {

    /** Tabla de §1.2 completa: los 5 tipos por los 3 formatos. */
    @ParameterizedTest
    @CsvSource({
        "MR,PDF,true", "MR,DOCX,false", "MR,XLSX,true",
        "TEC,PDF,true", "TEC,DOCX,true", "TEC,XLSX,false",
        "EJE,PDF,true", "EJE,DOCX,true", "EJE,XLSX,false",
        "MIT,PDF,true", "MIT,DOCX,true", "MIT,XLSX,true",
        "FIN,PDF,true", "FIN,DOCX,true", "FIN,XLSX,false"
    })
    void laListaBlancaCoincideConLaTablaDelPlan(TipoInforme tipo, FormatoInforme formato, boolean valido) {
        assertThat(tipo.admite(formato)).isEqualTo(valido);
        if (valido) {
            assertThatCode(() -> tipo.requerirFormato(formato)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> tipo.requerirFormato(formato)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unFormatoNuloNoEsValido() {
        for (TipoInforme tipo : TipoInforme.values()) {
            assertThat(tipo.admite(null)).isFalse();
            assertThatThrownBy(() -> tipo.requerirFormato(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void todosLosTiposAdmitenPdfComoVersionDeReferencia() {
        for (TipoInforme tipo : TipoInforme.values()) {
            assertThat(tipo.formatos()).contains(FormatoInforme.PDF);
        }
    }

    @Test
    void losConjuntosDeFormatosSonInmutables() {
        Set<FormatoInforme> formatos = TipoInforme.MR.formatos();
        assertThatThrownBy(() -> formatos.add(FormatoInforme.DOCX)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(TipoInforme.MR.formatos()).containsExactlyInAnyOrder(FormatoInforme.PDF, FormatoInforme.XLSX);
        assertThat(EnumSet.allOf(FormatoInforme.class)).hasSize(3);
    }

    @Test
    void losCodigosSonLosDeLaBaseDeDatos() {
        assertThat(TipoInforme.values()).extracting(Enum::name).containsExactly("MR", "TEC", "EJE", "MIT", "FIN");
        assertThat(TipoInforme.MR.nombre()).isEqualTo("Matriz de riesgo");
        assertThat(TipoInforme.FIN.nombre()).isEqualTo("Final de entrega");
    }

    @Test
    void cadaFormatoTieneMimeYExtensionExactos() {
        assertThat(FormatoInforme.PDF.mime()).isEqualTo("application/pdf");
        assertThat(FormatoInforme.PDF.extension()).isEqualTo("pdf");
        assertThat(FormatoInforme.DOCX.mime())
            .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(FormatoInforme.DOCX.extension()).isEqualTo("docx");
        assertThat(FormatoInforme.XLSX.mime())
            .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(FormatoInforme.XLSX.extension()).isEqualTo("xlsx");
    }
}
