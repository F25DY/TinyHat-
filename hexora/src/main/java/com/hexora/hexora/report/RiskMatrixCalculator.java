package com.hexora.hexora.report;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure functions shared by the assembler and the future renderers (PDF summary, XLSX matrix). */
public final class RiskMatrixCalculator {

    /** Risk levels in descending severity, as stored by the risk engine. */
    public static final List<String> LEVELS = List.of("Critico", "Alto", "Medio", "Bajo");

    public static final List<String> BAND_LABELS = List.of("0-1", "2-3", "4-5", "6-7", "8-10");

    private RiskMatrixCalculator() {
    }

    /** Maps a 0-10 value to a band index 0..4; out-of-range values are clamped. */
    public static int band(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        int whole = value.setScale(0, java.math.RoundingMode.FLOOR).intValue();
        return Math.max(0, Math.min(BAND_LABELS.size() - 1, whole / 2));
    }

    public static int band(int value) {
        return band(BigDecimal.valueOf(value));
    }

    public static ReportSnapshot.RiskMatrix matrix(List<ReportSnapshot.FindingEntry> findings) {
        int size = BAND_LABELS.size();
        int[][] counts = new int[size][size];
        for (ReportSnapshot.FindingEntry finding : findings) {
            counts[band(finding.probability())][band(finding.impact())]++;
        }
        List<List<Integer>> cells = new ArrayList<>(size);
        for (int[] row : counts) {
            List<Integer> cellRow = new ArrayList<>(size);
            for (int value : row) {
                cellRow.add(value);
            }
            cells.add(List.copyOf(cellRow));
        }
        return new ReportSnapshot.RiskMatrix(BAND_LABELS, List.copyOf(cells));
    }

    /** Counts per risk level, always listing the four levels in severity order (zeros included). */
    public static Map<String, Integer> countByLevel(List<ReportSnapshot.FindingEntry> findings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        LEVELS.forEach(level -> counts.put(level, 0));
        for (ReportSnapshot.FindingEntry finding : findings) {
            String level = canonicalLevel(finding.riskLevel());
            if (level != null) {
                counts.merge(level, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Overall posture: the most severe level that has at least one open or in-progress finding. */
    public static String overallRisk(List<ReportSnapshot.FindingEntry> findings) {
        if (findings.isEmpty()) {
            return "SIN_HALLAZGOS";
        }
        for (String level : LEVELS) {
            boolean present = findings.stream().anyMatch(finding ->
                level.equals(canonicalLevel(finding.riskLevel())) && isUnresolved(finding.status()));
            if (present) {
                return level.toUpperCase(Locale.ROOT);
            }
        }
        return "CONTROLADO";
    }

    public static boolean isUnresolved(String status) {
        return "Abierta".equals(status) || "En Progreso".equals(status);
    }

    /** Position of a level in severity order; unknown levels sort last. */
    public static int severityRank(String level) {
        String canonical = canonicalLevel(level);
        int index = canonical == null ? -1 : LEVELS.indexOf(canonical);
        return index < 0 ? LEVELS.size() : index;
    }

    static String canonicalLevel(String level) {
        if (level == null) {
            return null;
        }
        String normalized = Normalizer.normalize(level, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        return LEVELS.stream()
            .filter(candidate -> candidate.toLowerCase(Locale.ROOT).equals(normalized))
            .findFirst()
            .orElse(null);
    }
}
