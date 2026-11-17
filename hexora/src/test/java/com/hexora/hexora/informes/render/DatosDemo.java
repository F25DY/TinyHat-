package com.hexora.hexora.informes.render;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.Entrada;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteArea;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteCve;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.EntradaHistorial;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.EntradaTrazabilidad;
import com.hexora.hexora.informes.snapshot.ReferenciasApa;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de demostración <b>ficticios</b> para generar muestras de los informes. Nada de esto es real: la organización,
 * el proyecto, los hallazgos y los CVE (rango CVE-2099-xxxx, que no existe) se inventaron para las pruebas.
 */
public final class DatosDemo {

    public static final Instant EMISION = Instant.parse("2026-11-20T15:15:00Z");
    public static final String ORG_UUID_PUBLICO = "7f3a1c00-0000-4000-8000-0000000000aa";
    public static final String PRY_UUID_PUBLICO = "0c91e200-0000-4000-8000-0000000000bb";
    static final String ORG_UUID = "7f3a1c00-0000-4000-8000-0000000000aa";
    static final String PRY_UUID = "0c91e200-0000-4000-8000-0000000000bb";

    public static final FuenteArea RED = new FuenteArea("a-red", "Infraestructura de red", "Servidores, cortafuegos y segmentación de la red corporativa.");
    public static final FuenteArea WEB = new FuenteArea("a-web", "Aplicaciones web", "Portal de ventas y servicios de pedidos en línea.");
    public static final FuenteArea IDENTIDAD = new FuenteArea("a-id", "Gestión de identidades", "Autenticación, contraseñas y privilegios de las cuentas.");

    private DatosDemo() {
    }

    public static InformeSnapshot snapshot(TipoInforme tipo, boolean noPublicado) {
        return snapshot(tipo, noPublicado, true);
    }

    /** @param completo false deja la narrativa vacía (para probar los faltantes de una copia NP). */
    public static InformeSnapshot snapshot(TipoInforme tipo, boolean noPublicado, boolean completo) {
        return ConstructorSnapshot.construir(entrada(tipo, noPublicado, completo ? narrativa(tipo) : Map.of(),
            vulnerabilidades(), List.of(RED, WEB, IDENTIDAD)));
    }

    public static InformeSnapshot snapshotSinHallazgos(TipoInforme tipo) {
        return ConstructorSnapshot.construir(entrada(tipo, false, narrativa(tipo), List.of(), List.of(RED)));
    }

    public static InformeSnapshot snapshotConVulnerabilidades(TipoInforme tipo, List<FuenteVulnerabilidad> vs) {
        return ConstructorSnapshot.construir(entrada(tipo, false, narrativa(tipo), vs, List.of(RED, WEB, IDENTIDAD)));
    }

    public static Entrada entrada(
        TipoInforme tipo, boolean noPublicado, Map<String, String> narrativa, List<FuenteVulnerabilidad> vs,
        List<FuenteArea> areas
    ) {
        List<EntradaHistorial> historial = List.of(
            new EntradaHistorial(1, "DESPUBLICADO", "2026-11-12T09:30:00-05:00", "2026-11-14T16:00:00-05:00",
                "Se corrigió la clasificación de dos hallazgos tras la revisión con el cliente."),
            new EntradaHistorial(2, noPublicado ? "BORRADOR" : "PUBLICADO",
                noPublicado ? null : "2026-11-20T10:15:00-05:00", null, null));
        List<EntradaTrazabilidad> trazas = List.of(
            new EntradaTrazabilidad("TH-2026-7F3A-0C91-TEC-v2", "TEC", "PDF", "3f2a9c41d8e07b5a6c1d2e4f90ab7c3d5e6f1a2b3c4d5e6f708192a3b4c5d6e7", "2026-11-18T14:00:00-05:00"),
            new EntradaTrazabilidad("TH-2026-7F3A-0C91-TEC-v2", "TEC", "DOCX", "9a8b7c6d5e4f30211203948576a6b7c8d9e0f1a2b3c4d5e6f708192a3b4c5d6e", "2026-11-18T14:00:00-05:00"),
            new EntradaTrazabilidad("TH-2026-7F3A-0C91-EJE-v1", "EJE", "PDF", "1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff", "2026-11-18T15:30:00-05:00"),
            new EntradaTrazabilidad("TH-2026-7F3A-0C91-MIT-v1", "MIT", "XLSX", "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", "2026-11-19T09:10:00-05:00"));
        return new Entrada(tipo, 2, noPublicado ? "BORRADOR" : "PUBLICADO", noPublicado, 2026, ORG_UUID, PRY_UUID,
            "Comercializadora Andina S.A.S. (ficticia)", "Evaluación de seguridad de la plataforma de ventas",
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 16), "Firma Auditora Demo S.A.S.", areas, vs, narrativa,
            trazas, EMISION, "2026.1", 1000, ReferenciasApa.desdeClasspath(), historial);
    }

    public static FuenteVulnerabilidad v(
        String seisHex, FuenteArea area, String titulo, int c, int i, int a, Integer p, String estado, String plan,
        String cve, FuenteCve cache
    ) {
        return new FuenteVulnerabilidad(seisHex + "00-0000-4000-8000-000000000001", area.clave(), titulo, c, i, a, p,
            estado, plan, cve, cache);
    }

    public static List<FuenteVulnerabilidad> vulnerabilidades() {
        FuenteCve cve1 = new FuenteCve(new BigDecimal("9.8"), LocalDateTime.of(2099, 1, 10, 3, 0),
            "https://proveedor.ejemplo/avisos/2099-0001\nhttps://proveedor.ejemplo/parches/2099-0001");
        FuenteCve cve2 = new FuenteCve(new BigDecimal("7.5"), LocalDateTime.of(2099, 3, 22, 12, 0),
            "https://proveedor.ejemplo/avisos/2099-0002");
        return List.of(
            v("a1b2c3", RED, "Servicio de administración remota expuesto a Internet", 9, 8, 6, 8, "Abierta",
                "Restringir el acceso por VPN y filtrar el puerto en el cortafuegos perimetral.", "CVE-2099-0001", cve1),
            v("b2c3d4", RED, "Segmentación insuficiente entre la red de invitados y la corporativa", 6, 5, 4, 5, "En Progreso",
                "Crear VLAN separadas y reglas de filtrado entre segmentos.", null, null),
            v("c3d4e5", RED, "Certificado TLS caducado en el balanceador", 3, 2, 5, 9, "Abierta",
                "Renovar el certificado y automatizar el aviso de vencimiento.", null, null),
            v("d4e5f6", WEB, "Inyección SQL en el buscador de productos", 10, 10, 7, 9, "Abierta",
                "Usar consultas parametrizadas y validar la entrada del usuario en el servidor.", "CVE-2099-0002", cve2),
            v("e5f6a7", WEB, "Falta de cabeceras de seguridad HTTP", 2, 1, 0, 6, "Mitigada",
                "Configurar Content-Security-Policy, X-Content-Type-Options y Strict-Transport-Security.", null, null),
            v("f6a7b8", WEB, "Exposición de información de depuración en errores", 4, 0, 0, 7, "Abierta",
                "Desactivar el modo de depuración en producción y mostrar mensajes de error genéricos.", null, null),
            v("a7b8c9", WEB, "Contraseñas de administrador por defecto en el panel de ñoquis y pedidos", 8, 8, 8, 4, "Abierta",
                "Cambiar las credenciales de fábrica y exigir contraseñas de al menos doce caracteres.", null, null),
            v("b8c9d0", IDENTIDAD, "Cuentas de usuarios desvinculados sin deshabilitar", 7, 5, 1, 6, "En Progreso",
                "Revisar mensualmente las cuentas activas y deshabilitar las de personas que ya no laboran.", null, null),
            v("c9d0e1", IDENTIDAD, "Autenticación de un solo factor en el acceso remoto", 8, 6, 2, 5, "Abierta",
                "Habilitar un segundo factor de autenticación para todas las cuentas con acceso remoto.", null, null),
            v("d0e1f2", IDENTIDAD, "Política de contraseñas permisiva", 5, 4, 1, 3, "Mitigada",
                "Aplicar longitud mínima, bloqueo por intentos fallidos y lista de contraseñas prohibidas.", null, null),
            v("e1f2a3", IDENTIDAD, "Registro de accesos sin conservación suficiente", 1, 3, 0, 2, "Abierta",
                "Conservar los registros de autenticación al menos 12 meses en un repositorio centralizado.", null, null),
            v("f2a3b4", WEB, "Riesgo asumido: biblioteca de interfaz con versión antigua", 4, 4, 2, 5, "Aceptada",
                "El cliente acepta el riesgo hasta el próximo ciclo de actualización.", null, null),
            v("a3b4c5", RED, "Riesgo asumido: acceso FTP heredado desde la red interna", 5, 5, 3, 4, "Aceptada",
                "El cliente acepta el riesgo; el servicio se retira en el segundo semestre.", null, null));
    }

    public static Map<String, String> narrativa(TipoInforme tipo) {
        Map<String, String> n = new LinkedHashMap<>();
        switch (tipo) {
            case EJE -> {
                n.put("resumenEjecutivo", """
                    Entre el **2 y el 16 de noviembre de 2026** se evaluó la seguridad de la plataforma de ventas de la \
                    organización. Se encontraron hallazgos que, si no se atienden, podrían afectar la **continuidad del servicio** \
                    y la **reserva de la información** de clientes y pedidos.

                    Los puntos más importantes son:

                    - Un servicio de administración quedó accesible desde Internet.
                    - El buscador del portal permite manipular la base de datos.
                    - Hay cuentas con contraseñas por defecto y sin segundo factor.
                    """);
                n.put("recomendaciones", """
                    1. Atender primero los hallazgos de severidad **Crítica** y **Alta** en las próximas semanas.
                    2. Designar un responsable por área para dar seguimiento al plan de mitigación.
                    3. Programar una verificación posterior para confirmar que los cambios surtieron efecto.
                    """);
            }
            case TEC -> {
                n.put("alcanceMetodologia", """
                    ### Alcance
                    La evaluación cubrió la **infraestructura de red**, las **aplicaciones web** y la **gestión de identidades** de la \
                    plataforma de ventas, entre el 2 y el 16 de noviembre de 2026.

                    ### Metodología
                    Se siguió la guía NIST SP 800-115 y se calificaron los hallazgos según la escala descrita más adelante.

                    | Fase | Actividad |
                    |---|---|
                    | 1 | Reconocimiento y revisión de la documentación |
                    | 2 | Pruebas de seguridad autorizadas |
                    | 3 | Calificación y registro de hallazgos |
                    """);
                n.put("conclusiones", """
                    La plataforma presenta riesgos relevantes en la exposición de servicios y en la validación de datos. \
                    Con las acciones del plan de mitigación el riesgo residual se reduce de forma significativa.
                    """);
            }
            case MIT -> n.put("introduccion", """
                Este plan agrupa las acciones de mitigación de los hallazgos del proyecto, ordenadas por **severidad**. \
                Las fases son una sugerencia: los plazos definitivos los acuerda el cliente.
                """);
            case MR -> n.put("notas", """
                La matriz refleja el estado del proyecto a la fecha de emisión. Los activos, responsables y planes del cliente \
                se completan en la hoja de trabajo.
                """);
            case FIN -> {
                n.put("cartaEntrega", """
                    Bogotá, 20 de noviembre de 2026

                    Señores **Comercializadora Andina S.A.S.**

                    Con la presente hacemos entrega formal de los resultados de la evaluación de seguridad de la plataforma de ventas.
                    """);
                n.put("resumenEjecutivo", "Se identificaron hallazgos críticos en la exposición de servicios y en la validación de datos; el plan de mitigación prioriza su atención.");
                n.put("alcanceMetodologia", "La evaluación cubrió red, aplicaciones web e identidades, conforme a NIST SP 800-115.");
                n.put("conclusiones", "El proyecto se entrega con los hallazgos documentados, calificados y con su plan de mitigación.");
            }
            default -> {
            }
        }
        return n;
    }
}
