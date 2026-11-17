package com.hexora.hexora.informes.markdown;

import com.hexora.hexora.informes.InformesProperties;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.BloqueCodigo;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Cita;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Encabezado;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Fragmento;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Lista;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Parrafo;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Separador;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Tabla;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Convierte la narrativa del auditor (Markdown) en salidas seguras (§5). El texto se analiza <b>una sola vez</b> con
 * commonmark-java más tablas GFM; el árbol se sanea con una lista blanca de nodos y de él salen tres resultados
 * (HTML, modelo de Word y texto plano).
 *
 * <ul>
 *   <li>Se permiten: párrafos, encabezados (niveles 3 y 4; un nivel 1 o 2 se baja a 3), negrita, cursiva, listas,
 *       código, citas, tablas, saltos de línea y separadores.</li>
 *   <li>{@code HtmlBlock} y {@code HtmlInline} pasan a <b>texto escapado</b>.</li>
 *   <li>{@code Image} se elimina.</li>
 *   <li>{@code Link} se muestra como texto {@code etiqueta (url)}, sin hipervínculo en ningún formato; la URL solo
 *       se muestra si es http, https o mailto.</li>
 * </ul>
 *
 * Es seguro por construcción: tras sanear, {@link #verificar} comprueba que no queda ningún nodo fuera de la lista
 * blanca. El HTML sale con {@code escapeHtml(true)}, {@code sanitizeUrls(true)} y sin ningún atributo.
 */
@Component
public class MarkdownSaneado {

    private static final List<Extension> EXTENSIONES = List.of(TablesExtension.create());

    private static final Set<Class<? extends Node>> PERMITIDOS = Set.of(
        Document.class, Paragraph.class, Heading.class, StrongEmphasis.class, Emphasis.class, BulletList.class,
        OrderedList.class, ListItem.class, Code.class, FencedCodeBlock.class, IndentedCodeBlock.class,
        BlockQuote.class, TableBlock.class, TableHead.class, TableBody.class, TableRow.class, TableCell.class,
        SoftLineBreak.class, HardLineBreak.class, Text.class, ThematicBreak.class);

    /** Anidación máxima aceptada (citas dentro de citas, listas dentro de listas...). Evita desbordar la pila. */
    static final int MAX_PROFUNDIDAD = 40;

    private static final int NIVEL_MINIMO = 3;
    private static final int NIVEL_MAXIMO = 4;

    private final Parser analizador = Parser.builder().extensions(EXTENSIONES).build();
    private final HtmlRenderer generadorHtml = HtmlRenderer.builder()
        .extensions(EXTENSIONES)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .attributeProviderFactory(contexto -> (nodo, etiqueta, atributos) -> atributos.clear())
        .build();

    private final int maxCaracteres;

    @Autowired
    public MarkdownSaneado(InformesProperties propiedades) {
        this(propiedades.getMaxNarrativaCaracteres());
    }

    public MarkdownSaneado(int maxCaracteres) {
        this.maxCaracteres = maxCaracteres;
    }

    /**
     * Valida la longitud de una sección (máximo de caracteres por sección).
     *
     * @throws InformeValidacionException si el texto es más largo que el máximo
     */
    public void validarLongitud(String seccion, String markdown) {
        if (markdown != null && markdown.length() > maxCaracteres) {
            throw new InformeValidacionException(
                "La sección " + seccion + " supera el máximo de " + maxCaracteres + " caracteres.");
        }
    }

    /** Analiza y sanea una sección; un texto nulo o en blanco da una narrativa vacía. */
    public NarrativaSaneada analizar(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return NarrativaSaneada.VACIA;
        }
        Node nodo = analizador.parse(markdown);
        Document documento = (Document) nodo;
        if (profundidad(documento) > MAX_PROFUNDIDAD) {
            throw new InformeValidacionException("El texto tiene demasiados niveles de anidación.");
        }
        sanear(documento);
        verificar(documento);
        return new NarrativaSaneada(
            generadorHtml.render(documento).strip(),
            aTextoPlano(documento),
            aBloques(documento));
    }

    /** Profundidad máxima del árbol, sin recursión (el texto es del usuario). */
    static int profundidad(Document documento) {
        int maxima = 0;
        int actual = 0;
        Node nodo = documento.getFirstChild();
        if (nodo != null) {
            actual = 1;
            maxima = 1;
        }
        while (nodo != null) {
            Node hijo = nodo.getFirstChild();
            if (hijo != null) {
                nodo = hijo;
                actual++;
                maxima = Math.max(maxima, actual);
                continue;
            }
            while (nodo != null && nodo.getNext() == null) {
                nodo = nodo.getParent();
                actual--;
                if (nodo == documento) {
                    nodo = null;
                }
            }
            if (nodo != null) {
                nodo = nodo.getNext();
            }
        }
        return maxima;
    }

    // ---------------------------------------------------------------- saneado

    private void sanear(Document documento) {
        List<Node> nodos = new ArrayList<>();
        recolectar(documento, nodos);
        for (Node nodo : nodos) {
            if (enDocumento(nodo, documento)) {
                tratar(nodo);
            }
        }
    }

    private static void recolectar(Node padre, List<Node> nodos) {
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            nodos.add(hijo);
            recolectar(hijo, nodos);
        }
    }

    private static boolean enDocumento(Node nodo, Document documento) {
        for (Node actual = nodo; actual != null; actual = actual.getParent()) {
            if (actual == documento) {
                return true;
            }
        }
        return false;
    }

    private static void tratar(Node nodo) {
        if (nodo instanceof Link enlace) {
            reemplazarPorTexto(enlace, textoDeEnlace(enlace));
        } else if (nodo instanceof Image) {
            nodo.unlink();
        } else if (nodo instanceof HtmlInline html) {
            reemplazarPorTexto(html, html.getLiteral());
        } else if (nodo instanceof HtmlBlock html) {
            Paragraph parrafo = new Paragraph();
            parrafo.appendChild(new Text(quitarSaltoFinal(html.getLiteral())));
            nodo.insertBefore(parrafo);
            nodo.unlink();
        } else if (nodo instanceof LinkReferenceDefinition) {
            nodo.unlink();
        } else if (nodo instanceof Heading encabezado) {
            encabezado.setLevel(Math.max(NIVEL_MINIMO, Math.min(NIVEL_MAXIMO, encabezado.getLevel())));
        } else if (!PERMITIDOS.contains(nodo.getClass())) {
            desenvolver(nodo);
        }
    }

    private static void reemplazarPorTexto(Node nodo, String texto) {
        if (!texto.isEmpty()) {
            nodo.insertBefore(new Text(texto));
        }
        nodo.unlink();
    }

    /** Un nodo desconocido se sustituye por sus hijos, de modo que su contenido no se pierde ni se interpreta. */
    private static void desenvolver(Node nodo) {
        Node hijo = nodo.getFirstChild();
        while (hijo != null) {
            Node siguiente = hijo.getNext();
            nodo.insertBefore(hijo);
            hijo = siguiente;
        }
        nodo.unlink();
    }

    /** {@code etiqueta (url)}; la URL solo se muestra si es http, https o mailto (nunca {@code javascript:} y similares). */
    private static String textoDeEnlace(Link enlace) {
        String etiqueta = textoSimple(enlace).strip();
        String destino = enlace.getDestination() == null ? "" : enlace.getDestination().strip();
        String esquema = destino.toLowerCase(Locale.ROOT);
        boolean seMuestra = esquema.startsWith("http://") || esquema.startsWith("https://")
            || esquema.startsWith("mailto:");
        if (!seMuestra) {
            return etiqueta;
        }
        if (etiqueta.isEmpty() || etiqueta.equals(destino)) {
            return destino;
        }
        return etiqueta + " (" + destino + ")";
    }

    private static String textoSimple(Node nodo) {
        StringBuilder texto = new StringBuilder();
        for (Node hijo = nodo.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            if (hijo instanceof Text t) {
                texto.append(t.getLiteral());
            } else if (hijo instanceof Code c) {
                texto.append(c.getLiteral());
            } else if (hijo instanceof SoftLineBreak || hijo instanceof HardLineBreak) {
                texto.append(' ');
            } else if (!(hijo instanceof Image)) {
                texto.append(textoSimple(hijo));
            }
        }
        return texto.toString();
    }

    private static String quitarSaltoFinal(String texto) {
        return texto == null ? "" : texto.stripTrailing();
    }

    /** Defensa final: ningún nodo fuera de la lista blanca puede llegar a una salida. */
    private static void verificar(Node padre) {
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            if (!PERMITIDOS.contains(hijo.getClass())) {
                throw new IllegalStateException("Nodo de Markdown no permitido tras el saneado");
            }
            verificar(hijo);
        }
    }

    // ---------------------------------------------------------------- texto plano

    private static String aTextoPlano(Node documento) {
        return bloquesATexto(documento).strip();
    }

    private static String bloquesATexto(Node padre) {
        List<String> partes = new ArrayList<>();
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            partes.add(bloqueATexto(hijo));
        }
        return String.join("\n\n", partes);
    }

    private static String bloqueATexto(Node bloque) {
        if (bloque instanceof BulletList || bloque instanceof OrderedList) {
            boolean ordenada = bloque instanceof OrderedList;
            List<String> lineas = new ArrayList<>();
            int numero = 1;
            for (Node item = bloque.getFirstChild(); item != null; item = item.getNext()) {
                String marca = ordenada ? numero++ + ". " : "- ";
                lineas.add(marca + bloquesATexto(item).replace("\n\n", "\n").replace("\n", "\n  "));
            }
            return String.join("\n", lineas);
        }
        if (bloque instanceof FencedCodeBlock codigo) {
            return quitarSaltoFinal(codigo.getLiteral());
        }
        if (bloque instanceof IndentedCodeBlock codigo) {
            return quitarSaltoFinal(codigo.getLiteral());
        }
        if (bloque instanceof BlockQuote) {
            return bloquesATexto(bloque);
        }
        if (bloque instanceof ThematicBreak) {
            return "---";
        }
        if (bloque instanceof TableBlock) {
            List<String> filas = new ArrayList<>();
            for (Node seccion = bloque.getFirstChild(); seccion != null; seccion = seccion.getNext()) {
                for (Node fila = seccion.getFirstChild(); fila != null; fila = fila.getNext()) {
                    List<String> celdas = new ArrayList<>();
                    for (Node celda = fila.getFirstChild(); celda != null; celda = celda.getNext()) {
                        celdas.add(enLineaATexto(celda).strip());
                    }
                    filas.add(String.join(" | ", celdas));
                }
            }
            return String.join("\n", filas);
        }
        return enLineaATexto(bloque);
    }

    private static String enLineaATexto(Node padre) {
        StringBuilder texto = new StringBuilder();
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            if (hijo instanceof Text t) {
                texto.append(t.getLiteral());
            } else if (hijo instanceof Code c) {
                texto.append(c.getLiteral());
            } else if (hijo instanceof SoftLineBreak) {
                texto.append(' ');
            } else if (hijo instanceof HardLineBreak) {
                texto.append('\n');
            } else {
                texto.append(enLineaATexto(hijo));
            }
        }
        return texto.toString();
    }

    // ---------------------------------------------------------------- modelo para Word

    private static List<BloqueNarrativa> aBloques(Node padre) {
        List<BloqueNarrativa> bloques = new ArrayList<>();
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            BloqueNarrativa bloque = aBloque(hijo);
            if (bloque != null) {
                bloques.add(bloque);
            }
        }
        return bloques;
    }

    private static BloqueNarrativa aBloque(Node nodo) {
        if (nodo instanceof Paragraph) {
            return new Parrafo(fragmentos(nodo, false, false));
        }
        if (nodo instanceof Heading encabezado) {
            return new Encabezado(encabezado.getLevel(), fragmentos(nodo, false, false));
        }
        if (nodo instanceof BulletList || nodo instanceof OrderedList) {
            List<List<BloqueNarrativa>> elementos = new ArrayList<>();
            for (Node item = nodo.getFirstChild(); item != null; item = item.getNext()) {
                elementos.add(aBloques(item));
            }
            return new Lista(nodo instanceof OrderedList, elementos);
        }
        if (nodo instanceof FencedCodeBlock codigo) {
            return new BloqueCodigo(quitarSaltoFinal(codigo.getLiteral()));
        }
        if (nodo instanceof IndentedCodeBlock codigo) {
            return new BloqueCodigo(quitarSaltoFinal(codigo.getLiteral()));
        }
        if (nodo instanceof BlockQuote) {
            return new Cita(aBloques(nodo));
        }
        if (nodo instanceof ThematicBreak) {
            return new Separador();
        }
        if (nodo instanceof TableBlock) {
            List<List<List<Fragmento>>> filas = new ArrayList<>();
            boolean conEncabezado = false;
            for (Node seccion = nodo.getFirstChild(); seccion != null; seccion = seccion.getNext()) {
                conEncabezado |= seccion instanceof TableHead;
                for (Node fila = seccion.getFirstChild(); fila != null; fila = fila.getNext()) {
                    List<List<Fragmento>> celdas = new ArrayList<>();
                    for (Node celda = fila.getFirstChild(); celda != null; celda = celda.getNext()) {
                        celdas.add(fragmentos(celda, false, false));
                    }
                    filas.add(celdas);
                }
            }
            return new Tabla(filas, conEncabezado);
        }
        return null;
    }

    private static List<Fragmento> fragmentos(Node padre, boolean negrita, boolean cursiva) {
        List<Fragmento> resultado = new ArrayList<>();
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNext()) {
            if (hijo instanceof Text t) {
                resultado.add(new Fragmento(t.getLiteral(), negrita, cursiva, false));
            } else if (hijo instanceof Code c) {
                resultado.add(new Fragmento(c.getLiteral(), negrita, cursiva, true));
            } else if (hijo instanceof SoftLineBreak) {
                resultado.add(new Fragmento(" ", negrita, cursiva, false));
            } else if (hijo instanceof HardLineBreak) {
                resultado.add(Fragmento.plano("\n"));
            } else if (hijo instanceof StrongEmphasis) {
                resultado.addAll(fragmentos(hijo, true, cursiva));
            } else if (hijo instanceof Emphasis) {
                resultado.addAll(fragmentos(hijo, negrita, true));
            } else {
                resultado.addAll(fragmentos(hijo, negrita, cursiva));
            }
        }
        return resultado;
    }
}
