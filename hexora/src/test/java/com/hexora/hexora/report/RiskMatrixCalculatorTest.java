package com.hexora.hexora.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskMatrixCalculatorTest {

    @Test
    void mapsValuesToTheFiveBandsAndClampsOutOfRangeInput() {
        assertThat(RiskMatrixCalculator.band(0)).isZero();
        assertThat(RiskMatrixCalculator.band(1)).isZero();
        assertThat(RiskMatrixCalculator.band(2)).isEqualTo(1);
        assertThat(RiskMatrixCalculator.band(5)).isEqualTo(2);
        assertThat(RiskMatrixCalculator.band(7)).isEqualTo(3);
        assertThat(RiskMatrixCalculator.band(8)).isEqualTo(4);
        assertThat(RiskMatrixCalculator.band(10)).isEqualTo(4);
        assertThat(RiskMatrixCalculator.band(new BigDecimal("3.99"))).isEqualTo(1);
        assertThat(RiskMatrixCalculator.band(new BigDecimal("-4"))).isZero();
        assertThat(RiskMatrixCalculator.band(new BigDecimal("99"))).isEqualTo(4);
        assertThat(RiskMatrixCalculator.band((BigDecimal) null)).isZero();
    }

    @Test
    void buildsCountsAndOverallPostureFromFindings() {
        List<ReportSnapshot.FindingEntry> findings = List.of(
            finding("Critico", "Abierta", 9, new BigDecimal("8.67")),
            finding("Alto", "Mitigada", 6, new BigDecimal("6.00")),
            finding("Medio", "En Progreso", 3, new BigDecimal("3.33")));

        ReportSnapshot.RiskMatrix matrix = RiskMatrixCalculator.matrix(findings);
        assertThat(matrix.bandLabels()).containsExactly("0-1", "2-3", "4-5", "6-7", "8-10");
        assertThat(matrix.cells()).hasSize(5).allSatisfy(row -> assertThat(row).hasSize(5));
        assertThat(matrix.cells().get(4).get(4)).isEqualTo(1);
        assertThat(matrix.cells().get(3).get(3)).isEqualTo(1);
        assertThat(matrix.cells().get(1).get(1)).isEqualTo(1);
        assertThat(matrix.cells().stream().flatMap(List::stream).mapToInt(Integer::intValue).sum()).isEqualTo(3);

        assertThat(RiskMatrixCalculator.countByLevel(findings))
            .containsExactly(
                java.util.Map.entry("Critico", 1), java.util.Map.entry("Alto", 1),
                java.util.Map.entry("Medio", 1), java.util.Map.entry("Bajo", 0));
        assertThat(RiskMatrixCalculator.overallRisk(findings)).isEqualTo("CRITICO");
    }

    @Test
    void overallPostureIgnoresResolvedFindingsAndHandlesEmptyProjects() {
        assertThat(RiskMatrixCalculator.overallRisk(List.of())).isEqualTo("SIN_HALLAZGOS");
        assertThat(RiskMatrixCalculator.overallRisk(List.of(
            finding("Critico", "Mitigada", 9, new BigDecimal("9")),
            finding("Alto", "Aceptada", 9, new BigDecimal("9"))))).isEqualTo("CONTROLADO");
        assertThat(RiskMatrixCalculator.overallRisk(List.of(
            finding("Critico", "Mitigada", 9, new BigDecimal("9")),
            finding("Medio", "Abierta", 4, new BigDecimal("4"))))).isEqualTo("MEDIO");
    }

    @Test
    void severityRankAcceptsAccentsAndSortsUnknownLevelsLast() {
        assertThat(RiskMatrixCalculator.severityRank("Crítico")).isZero();
        assertThat(RiskMatrixCalculator.severityRank("ALTO")).isEqualTo(1);
        assertThat(RiskMatrixCalculator.severityRank("raro")).isEqualTo(RiskMatrixCalculator.LEVELS.size());
        assertThat(RiskMatrixCalculator.severityRank(null)).isEqualTo(RiskMatrixCalculator.LEVELS.size());
    }

    private ReportSnapshot.FindingEntry finding(String level, String status, int probability, BigDecimal impact) {
        return new ReportSnapshot.FindingEntry("id", "area", "Área", "Hallazgo", "Tipo", null, null, null, null,
            null, null, null, 5, 5, 5, probability, impact, level, status, "2026-11-11", null);
    }
}
