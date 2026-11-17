package com.hexora.hexora.informes.markdown;

import java.util.List;

/**
 * Modelo neutral de la narrativa ya saneada, pensado para el renderizador de Word (párrafos y fragmentos con
 * estilos de la plantilla base). Solo contiene texto plano y formato: ningún enlace, imagen ni HTML.
 */
public sealed interface BloqueNarrativa {

    /** Trozo de texto con su formato. Un salto de línea duro es un fragmento con el texto {@code "\n"}. */
    record Fragmento(String texto, boolean negrita, boolean cursiva, boolean codigo) {
        public static Fragmento plano(String texto) {
            return new Fragmento(texto, false, false, false);
        }

        public boolean esSaltoDeLinea() {
            return "\n".equals(texto);
        }
    }

    record Parrafo(List<Fragmento> fragmentos) implements BloqueNarrativa {
        public Parrafo {
            fragmentos = List.copyOf(fragmentos);
        }
    }

    /** Nivel 3 o 4: la plantilla usa los niveles 1 y 2, la narrativa del 3 en adelante. */
    record Encabezado(int nivel, List<Fragmento> fragmentos) implements BloqueNarrativa {
        public Encabezado {
            fragmentos = List.copyOf(fragmentos);
        }
    }

    record Lista(boolean ordenada, List<List<BloqueNarrativa>> elementos) implements BloqueNarrativa {
        public Lista {
            elementos = elementos.stream().map(List::copyOf).toList();
        }
    }

    record BloqueCodigo(String texto) implements BloqueNarrativa {
    }

    record Cita(List<BloqueNarrativa> bloques) implements BloqueNarrativa {
        public Cita {
            bloques = List.copyOf(bloques);
        }
    }

    /** Filas de celdas; cada celda es una lista de fragmentos. La primera fila es el encabezado si {@code conEncabezado}. */
    record Tabla(List<List<List<Fragmento>>> filas, boolean conEncabezado) implements BloqueNarrativa {
        public Tabla {
            filas = filas.stream().map(fila -> fila.stream().map(List::copyOf).toList()).toList();
        }
    }

    record Separador() implements BloqueNarrativa {
    }
}
