package com.hexora.hexora.informes.servicio;

import com.hexora.hexora.informes.EstadoVersion;
import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.InformesProperties;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.RegistroRenderers;
import com.hexora.hexora.informes.render.pdf.VistaPreviaInforme;
import com.hexora.hexora.informes.servicio.NotificadorInformes.InformePublicadoEvento;
import com.hexora.hexora.informes.snapshot.EnsambladorSnapshot;
import com.hexora.hexora.informes.snapshot.GeneradorIdInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.SerializadorSnapshot;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import com.hexora.hexora.informes.validacion.ValidadorPublicacion;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Informe;
import com.hexora.hexora.model.InformeArchivo;
import com.hexora.hexora.model.InformeVersion;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.InformeArchivoRepository;
import com.hexora.hexora.repository.InformeRepository;
import com.hexora.hexora.repository.InformeVersionRepository;
import com.hexora.hexora.security.AuditEventType;
import com.hexora.hexora.security.RateLimitExceededException;
import com.hexora.hexora.security.RateLimitService;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.TinyHatSecurityProperties;
import com.hexora.hexora.service.PermissionService;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ciclo de vida de los informes (§8): crear borrador, editar la narrativa, previsualizar, generar copias NO PUBLICADAS,
 * publicar de forma atómica, despublicar, eliminar borradores, verificar y descargar. No depende de ningún controlador:
 * recibe al usuario autenticado y flujos de salida, de modo que puede moverse a segundo plano sin reescribirse.
 *
 * <p>Seguridad: solo un AUDITOR asignado al proyecto puede crear, editar, generar, publicar o despublicar (cualquier
 * otro rol o una cuenta ajena recibe el mismo "no encontrado" que un recurso inexistente); el CLIENTE_ADMIN solo
 * verifica y descarga versiones PUBLICADAS de su organización. Los datos salen de la base y del snapshot; no hay red.
 */
@Service
public class InformeService {

    private static final Logger LOG = LoggerFactory.getLogger(InformeService.class);
    private static final String ROL_AUDITOR = "AUDITOR";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> MAPA_TEXTOS = new TypeReference<>() {
    };

    /** Archivo publicado, para el resultado de publicar y de verificar. */
    public record ArchivoPublicado(String formato, String sha256, long bytes) {
    }

    public record ResultadoPublicacion(String versionPublicId, String snapshotSha256, List<ArchivoPublicado> archivos) {
    }

    public record Verificacion(
        String id, int version, String estado, String snapshotSha256, List<ArchivoPublicado> archivos,
        LocalDateTime publicadoEn, LocalDateTime despublicadoEn
    ) {
    }

    /** Archivo ya verificado contra su hash, listo para servir con {@code nosniff} y {@code no-store}. */
    public record Descarga(byte[] contenido, String mime, String nombre, String sha256, FormatoInforme formato) {
    }

    private final PermissionService permisos;
    private final InformeRepository informes;
    private final InformeVersionRepository versiones;
    private final InformeArchivoRepository archivos;
    private final AuditoriaRepository proyectos;
    private final EnsambladorSnapshot ensamblador;
    private final ValidadorPublicacion validador;
    private final MarkdownSaneado markdown;
    private final RegistroRenderers renderizadores;
    private final AlmacenInformes almacen;
    private final ControlConcurrenciaInformes concurrencia;
    private final AuditoriaInformes auditoria;
    private final RateLimitService limitador;
    private final TinyHatSecurityProperties seguridad;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final VistaPreviaInforme vistaPrevia;

    public InformeService(
        PermissionService permisos,
        InformeRepository informes,
        InformeVersionRepository versiones,
        InformeArchivoRepository archivos,
        AuditoriaRepository proyectos,
        EnsambladorSnapshot ensamblador,
        ValidadorPublicacion validador,
        MarkdownSaneado markdown,
        RegistroRenderers renderizadores,
        AlmacenInformes almacen,
        ControlConcurrenciaInformes concurrencia,
        AuditoriaInformes auditoria,
        RateLimitService limitador,
        TinyHatSecurityProperties seguridad,
        ApplicationEventPublisher eventos,
        InformesProperties propiedades,
        Clock reloj,
        VistaPreviaInforme vistaPrevia
    ) {
        this.permisos = permisos;
        this.informes = informes;
        this.versiones = versiones;
        this.archivos = archivos;
        this.proyectos = proyectos;
        this.ensamblador = ensamblador;
        this.validador = validador;
        this.markdown = markdown;
        this.renderizadores = renderizadores;
        this.almacen = almacen;
        this.concurrencia = concurrencia;
        this.auditoria = auditoria;
        this.limitador = limitador;
        this.seguridad = seguridad;
        this.eventos = eventos;
        this.reloj = reloj;
        this.vistaPrevia = vistaPrevia;
    }

    // ================================================================ borrador y narrativa

    /**
     * Crea el borrador de la siguiente versión (I1: como máximo uno por serie). La narrativa de la última versión se
     * precarga (I6); los datos del proyecto siempre se leen vivos al generar o publicar.
     */
    @Transactional
    public InformeVersion crearBorrador(TinyHatPrincipal principal, String proyectoPublicId, TipoInforme tipo) {
        exigirAuditor(principal);
        Auditoria proyecto = permisos.requireProyecto(principal, proyectoPublicId);
        long cuenta = principal.getCuentaId();
        Informe serie = informes.findByProyectoIdAndTipoAndCuentaId(proyecto.getIdAuditoria(), tipo, cuenta)
            .orElseGet(() -> informes.saveAndFlush(new Informe(proyecto.getEmpresa().getId(), proyecto.getIdAuditoria(),
                tipo, (short) LocalDate.now(reloj).getYear(), principal.getUserId())));
        if (versiones.findByInformeIdAndCuentaIdAndEstado(serie.getId(), cuenta, EstadoVersion.BORRADOR).isPresent()) {
            throw new ErroresInforme.Conflicto(
                "Ya existe un borrador de este informe. Edítelo o elimínelo antes de crear otro.");
        }
        int numero = versiones.maxNumero(serie.getId(), cuenta) + 1;
        String narrativa = versiones.findFirstByInformeIdAndCuentaIdOrderByNumeroDesc(serie.getId(), cuenta)
            .map(InformeVersion::getNarrativaJson).orElse("{}");
        InformeVersion nueva = versiones.saveAndFlush(
            new InformeVersion(serie.getId(), numero, principal.getUserId(), narrativa));
        auditoria.ok(AuditEventType.INFORME_BORRADOR_CREADO, principal, nueva.getPublicId(),
            Map.of("tipo", tipo.name(), "version", numero));
        return nueva;
    }

    /** Guarda una sección de la narrativa (Markdown crudo; se sanea al renderizar). Solo en un borrador. */
    @Transactional
    public InformeVersion guardarNarrativa(
        TinyHatPrincipal principal, String versionPublicId, String seccion, String texto
    ) {
        exigirAuditor(principal);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        Informe serie = serieDe(version, principal.getCuentaId());
        TipoInforme.SeccionNarrativa definicion = serie.getTipo().secciones().stream()
            .filter(s -> s.clave().equals(seccion)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Sección desconocida"));
        if (version.getEstado() != EstadoVersion.BORRADOR) {
            throw new ErroresInforme.Conflicto("Solo un borrador puede editarse.");
        }
        markdown.validarLongitud(definicion.etiqueta(), texto);
        Map<String, String> narrativa = leerNarrativa(version);
        if (texto == null || texto.isBlank()) {
            narrativa.remove(seccion);
        } else {
            narrativa.put(seccion, texto);
        }
        version.actualizarNarrativa(JSON.writeValueAsString(new TreeMapOrdenado(narrativa)), ahoraUtc());
        InformeVersion guardada = versiones.saveAndFlush(version);
        auditoria.ok(AuditEventType.INFORME_NARRATIVA_ACTUALIZADA, principal, versionPublicId, Map.of("seccion", seccion));
        return guardada;
    }

    /** Elimina físicamente un borrador; lo publicado o despublicado nunca se borra (I3, I5). */
    @Transactional
    public void eliminarBorrador(TinyHatPrincipal principal, String versionPublicId) {
        exigirAuditor(principal);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        if (!version.getEstado().permiteEliminar()) {
            throw new ErroresInforme.Conflicto("Una versión publicada o despublicada no puede eliminarse.");
        }
        versiones.delete(version);
        versiones.flush();
        auditoria.ok(AuditEventType.INFORME_BORRADOR_ELIMINADO, principal, versionPublicId, Map.of());
    }

    // ================================================================ copias no publicadas

    /** Vista previa en PDF de un borrador (o de una versión publicada) con la marca de no publicado. */
    @Transactional(readOnly = true)
    public void previsualizar(TinyHatPrincipal principal, String versionPublicId, OutputStream salida) {
        generar(principal, versionPublicId, FormatoInforme.PDF, salida, AuditEventType.INFORME_PREVISUALIZADO);
    }

    /**
     * Genera una copia NO PUBLICADA en memoria (no se guarda): de los datos vivos si es un borrador, o del snapshot si es
     * una versión publicada (única forma de obtener un formato que no se eligió al publicar).
     */
    @Transactional(readOnly = true)
    public void generarNoPublicado(
        TinyHatPrincipal principal, String versionPublicId, FormatoInforme formato, OutputStream salida
    ) {
        generar(principal, versionPublicId, formato, salida, AuditEventType.INFORME_GENERADO);
    }

    private void generar(
        TinyHatPrincipal principal, String versionPublicId, FormatoInforme formato, OutputStream salida,
        AuditEventType evento
    ) {
        exigirAuditor(principal);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        Informe serie = serieDe(version, principal.getCuentaId());
        TipoInforme tipo = serie.getTipo();
        tipo.requerirFormato(formato);
        cupo(principal);
        boolean desdeBorrador = version.getEstado() == EstadoVersion.BORRADOR;
        concurrencia.ejecutar(() -> {
            try {
                InformeSnapshot snapshot = desdeBorrador
                    ? ensamblador.ensamblar(proyectoDe(serie, principal.getCuentaId()), tipo, version.getNumero(),
                        EstadoVersion.BORRADOR, true, serie.getAnio(), leerNarrativa(version), Instant.now(reloj))
                    : comoNoPublicado(SerializadorSnapshot.leer(version.getSnapshotJson()));
                renderizadores.resolver(tipo, formato).render(snapshot, new OpcionesRender(true),
                    new SalidaLimitada(salida, almacen.maximoBytes()));
                return null;
            } catch (IOException | RuntimeException e) {
                if (e instanceof InformeValidacionException validacion) {
                    throw validacion;
                }
                if (e instanceof IllegalArgumentException invalido) {
                    throw invalido;
                }
                LOG.error("Fallo al generar un informe ({})", e.getClass().getSimpleName());
                throw new ErroresInforme.Publicacion(e);
            }
        });
        Map<String, Object> detalle = evento == AuditEventType.INFORME_GENERADO
            ? Map.of("formato", formato.name(), "origen", desdeBorrador ? "BORRADOR" : "SNAPSHOT") : Map.of();
        auditoria.ok(evento, principal, versionPublicId, detalle);
    }

    /** Copia de un snapshot publicado para generar una copia de trabajo: el ID lleva el sufijo -NP. */
    static InformeSnapshot comoNoPublicado(InformeSnapshot s) {
        String id = s.informe().id().endsWith(GeneradorIdInforme.SUFIJO_NO_PUBLICADO) ? s.informe().id()
            : s.informe().id() + GeneradorIdInforme.SUFIJO_NO_PUBLICADO;
        return new InformeSnapshot(s.schemaVersion(), s.plantillaVersion(), s.generadoEn(),
            new InformeSnapshot.DatosInforme(s.informe().tipo(), s.informe().version(), id, s.informe().estado()),
            s.organizacion(), s.proyecto(), s.auditor(), s.metricas(), s.areas(), s.anexoA(), s.trazabilidad(),
            s.historial(), s.narrativa(), s.referenciasApa());
    }

    // ================================================================ publicar (atómico)

    /**
     * Publica un borrador. Orden (§8.2): autorizar y comprobar el estado y la vigencia; congelar el snapshot con los
     * datos vivos; validar sobre el snapshot; renderizar cada formato a un temporal calculando su SHA-256; y, en la
     * transacción, mover los archivos (atómico), registrarlos y publicar la versión. La auditoría y la notificación
     * salen <b>después</b> de confirmar. Si algo falla se borran temporales y archivos movidos y el borrador queda
     * intacto.
     */
    @Transactional
    public ResultadoPublicacion publicar(
        TinyHatPrincipal principal, String versionPublicId, Set<FormatoInforme> formatos
    ) {
        exigirAuditor(principal);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        long cuenta = principal.getCuentaId();
        Informe serie = serieDe(version, cuenta);
        TipoInforme tipo = serie.getTipo();
        if (version.getEstado() != EstadoVersion.BORRADOR) {
            throw new ErroresInforme.Conflicto("Solo un borrador puede publicarse.");
        }
        if (formatos == null || formatos.isEmpty()) {
            throw new IllegalArgumentException("Elija al menos un formato");
        }
        Set<FormatoInforme> elegidos = EnumSet.copyOf(formatos);
        elegidos.forEach(tipo::requerirFormato);
        if (!elegidos.contains(FormatoInforme.PDF)) {
            throw new ErroresInforme.Conflicto("El PDF es la versión de referencia y debe publicarse siempre.");
        }
        if (versiones.findByInformeIdAndCuentaIdAndEstado(serie.getId(), cuenta, EstadoVersion.PUBLICADO).isPresent()) {
            throw new ErroresInforme.Conflicto("Despublique la versión vigente antes de publicar una nueva.");
        }
        cupo(principal);
        return concurrencia.ejecutar(() -> publicarConCupo(principal, version, serie, tipo, elegidos));
    }

    private ResultadoPublicacion publicarConCupo(
        TinyHatPrincipal principal, InformeVersion version, Informe serie, TipoInforme tipo, Set<FormatoInforme> formatos
    ) {
        List<Path> temporales = new ArrayList<>();
        List<Path> movidos = new ArrayList<>();
        try {
            Auditoria proyecto = proyectoDe(serie, principal.getCuentaId());
            Instant ahora = Instant.now(reloj);
            InformeSnapshot snapshot = ensamblador.ensamblar(proyecto, tipo, version.getNumero(), EstadoVersion.PUBLICADO,
                false, serie.getAnio(), leerNarrativa(version), ahora);
            validador.validar(snapshot).exigirValido();
            String json = SerializadorSnapshot.serializar(snapshot);
            String huella = SerializadorSnapshot.sha256(json);

            List<Pieza> piezas = new ArrayList<>();
            for (FormatoInforme formato : formatos) {
                Path temporal = almacen.crearTemporal();
                temporales.add(temporal);
                AlmacenInformes.Escritura escritura = almacen.abrirEscritura(temporal);
                try (escritura) {
                    renderizadores.resolver(tipo, formato).render(snapshot, new OpcionesRender(false), escritura);
                }
                piezas.add(new Pieza(formato, temporal, escritura.sha256(), escritura.bytes()));
            }

            borrarSiSeRevierte(movidos);
            String organizacion = proyecto.getEmpresa().getPublicId();
            // Primero los archivos al disco; luego la version pasa a PUBLICADO y solo entonces se registran los archivos
            // (el disparador de la base exige que el archivo pertenezca a una version publicada, I4).
            List<String[]> destinos = new ArrayList<>();
            for (Pieza pieza : piezas) {
                String archivoUuid = UUID.randomUUID().toString();
                String ruta = AlmacenInformes.rutaRelativa(organizacion, serie.getPublicId(), version.getNumero(),
                    archivoUuid, pieza.formato());
                movidos.add(almacen.mover(pieza.temporal(), ruta));
                temporales.remove(pieza.temporal());
                destinos.add(new String[] {archivoUuid, ruta});
            }
            version.publicar(json, huella, snapshot.plantillaVersion(), LocalDateTime.ofInstant(ahora, ZoneOffset.UTC));
            versiones.saveAndFlush(version);
            List<ArchivoPublicado> publicados = new ArrayList<>();
            for (int i = 0; i < piezas.size(); i++) {
                Pieza pieza = piezas.get(i);
                archivos.saveAndFlush(new InformeArchivo(destinos.get(i)[0], version.getId(), pieza.formato(),
                    destinos.get(i)[1], pieza.sha256(), pieza.bytes()));
                publicados.add(new ArchivoPublicado(pieza.formato().name(), pieza.sha256(), pieza.bytes()));
            }

            avisarTrasConfirmar(principal, version, publicados, huella, serie.getOrganizacionId());
            return new ResultadoPublicacion(version.getPublicId(), huella, List.copyOf(publicados));
        } catch (InformeValidacionException e) {
            limpiar(temporales, movidos);
            fallida(principal, version, "VALIDACION");
            throw e;
        } catch (IOException | RuntimeException e) {
            limpiar(temporales, movidos);
            fallida(principal, version, "ERROR_TECNICO");
            LOG.error("Fallo al publicar un informe ({})", e.getClass().getSimpleName());
            throw new ErroresInforme.Publicacion(e);
        }
    }

    private record Pieza(FormatoInforme formato, Path temporal, String sha256, long bytes) {
    }

    private void limpiar(List<Path> temporales, List<Path> movidos) {
        temporales.forEach(almacen::borrarSilencioso);
        movidos.forEach(almacen::borrarSilencioso);
    }

    private void fallida(TinyHatPrincipal principal, InformeVersion version, String causa) {
        try {
            auditoria.registrar(AuditEventType.INFORME_PUBLICACION_FALLIDA, principal, "ERROR", version.getPublicId(),
                Map.of("causa", causa));
        } catch (RuntimeException e) {
            LOG.warn("No se pudo registrar la publicación fallida ({})", e.getClass().getSimpleName());
        }
    }

    /** Si la transacción se revierte (incluida una falla en el commit), los archivos ya movidos se borran. */
    private void borrarSiSeRevierte(List<Path> movidos) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado != STATUS_COMMITTED) {
                    movidos.forEach(almacen::borrarSilencioso);
                }
            }
        });
    }

    /** Auditoría y notificación solo después de confirmar la transacción. */
    private void avisarTrasConfirmar(
        TinyHatPrincipal principal, InformeVersion version, List<ArchivoPublicado> publicados, String huella,
        Long organizacionId
    ) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("formatos", publicados.stream().map(ArchivoPublicado::formato).toList());
        Map<String, String> hashes = new LinkedHashMap<>();
        publicados.forEach(a -> hashes.put(a.formato(), a.sha256()));
        detalle.put("hashes", hashes);
        detalle.put("snapshotSha256", huella);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    auditoria.ok(AuditEventType.INFORME_PUBLICADO, principal, version.getPublicId(), detalle);
                }
            });
        } else {
            auditoria.ok(AuditEventType.INFORME_PUBLICADO, principal, version.getPublicId(), detalle);
        }
        eventos.publishEvent(new InformePublicadoEvento(principal.getCuentaId(), organizacionId, version.getPublicId()));
    }

    // ================================================================ despublicar

    /** PUBLICADO → DESPUBLICADO con motivo obligatorio (10 a 500 caracteres). Los archivos se conservan como evidencia. */
    @Transactional
    public InformeVersion despublicar(TinyHatPrincipal principal, String versionPublicId, String motivo) {
        exigirAuditor(principal);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        if (version.getEstado() != EstadoVersion.PUBLICADO) {
            throw new ErroresInforme.Conflicto("Solo una versión publicada puede despublicarse.");
        }
        version.despublicar(motivo, ahoraUtc());
        InformeVersion guardada = versiones.saveAndFlush(version);
        auditoria.ok(AuditEventType.INFORME_DESPUBLICADO, principal, versionPublicId,
            Map.of("motivo", guardada.getMotivoDespublicacion()));
        return guardada;
    }

    // ================================================================ verificar y descargar

    /** Muestra el hash del contenido y el de cada archivo; no recibe archivos ni "valida" nada subido (§1.6). */
    @Transactional(readOnly = true)
    public Verificacion verificar(TinyHatPrincipal principal, String versionPublicId) {
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        if (version.getSnapshotSha256() == null) {
            throw new ErroresInforme.Conflicto("El informe aún no está publicado.");
        }
        Informe serie = serieDe(version, principal.getCuentaId());
        Auditoria proyecto = proyectoDe(serie, principal.getCuentaId());
        List<ArchivoPublicado> lista = archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(
            version.getId(), principal.getCuentaId()).stream()
            .map(a -> new ArchivoPublicado(a.getFormato().name(), a.getSha256(), a.getBytes())).toList();
        auditoria.ok(AuditEventType.INFORME_VERIFICADO, principal, versionPublicId, Map.of());
        return new Verificacion(idDe(serie, proyecto, version.getNumero()), version.getNumero(),
            version.getEstado().name(), version.getSnapshotSha256(), lista, version.getPublicadoEn(),
            version.getDespublicadoEn());
    }

    /**
     * Entrega un archivo publicado. Antes de servirlo se recalcula su SHA-256 sobre los mismos bytes que se entregan y se
     * compara con el de la base; si no coincide (o falta) se responde un error genérico, no se sirve nada y se registra
     * {@code INFORME_INTEGRIDAD_FALLIDA}.
     */
    @Transactional
    public Descarga descargar(TinyHatPrincipal principal, String archivoPublicId) {
        InformeArchivo archivo = permisos.requireInformeArchivo(principal, archivoPublicId);
        long cuenta = principal.getCuentaId();
        InformeVersion version = versiones.findById(archivo.getVersionId()).filter(v -> v.getCuentaId().equals(cuenta))
            .orElseThrow(ScopedNotFoundException::new);
        byte[] contenido;
        try {
            contenido = almacen.leer(archivo.getRutaRelativa());
        } catch (IOException | RuntimeException e) {
            integridadFallida(principal, version, archivo, "ARCHIVO_NO_DISPONIBLE");
            throw new ErroresInforme.Integridad();
        }
        String calculado = AlmacenInformes.sha256(contenido);
        if (!MessageDigest.isEqual(calculado.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            archivo.getSha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            integridadFallida(principal, version, archivo, "HASH_DISTINTO");
            throw new ErroresInforme.Integridad();
        }
        Informe serie = serieDe(version, cuenta);
        String nombre = idDe(serie, proyectoDe(serie, cuenta), version.getNumero()) + "." + archivo.getFormato().extension();
        auditoria.ok(AuditEventType.INFORME_DESCARGADO, principal, version.getPublicId(),
            Map.of("formato", archivo.getFormato().name(), "rol", principal.getRol()));
        return new Descarga(contenido, archivo.getMime(), nombre, calculado, archivo.getFormato());
    }

    private void integridadFallida(TinyHatPrincipal principal, InformeVersion version, InformeArchivo archivo, String causa) {
        auditoria.registrar(AuditEventType.INFORME_INTEGRIDAD_FALLIDA, principal, "ERROR", version.getPublicId(),
            Map.of("formato", archivo.getFormato().name(), "causa", causa));
    }

    // ================================================================ consultas para las vistas (Etapa 7)

    public record VersionResumen(
        String publicId, int numero, String estado, LocalDateTime publicadoEn, LocalDateTime despublicadoEn, String motivo
    ) {
    }

    public record TipoEnLista(TipoInforme tipo, boolean hayBorrador, boolean hayPublicada, List<VersionResumen> versiones) {
    }

    public record ListaProyecto(String proyectoPublicId, String proyectoNombre, List<TipoEnLista> tipos) {
    }

    public record SeccionEditable(String clave, String etiqueta, boolean obligatoria, String texto) {
    }

    public record ArchivoVista(String publicId, String formato, long bytes, String sha256) {
    }

    public record DetalleVersion(
        String publicId, String proyectoPublicId, String proyectoNombre, TipoInforme tipo, int numero, EstadoVersion estado,
        String id, List<SeccionEditable> secciones, List<FormatoInforme> formatos, List<String> faltantes,
        List<ArchivoVista> archivos, String snapshotSha256, LocalDateTime publicadoEn, LocalDateTime despublicadoEn,
        String motivo
    ) {
    }

    public record InformePublicadoVista(
        String versionPublicId, String id, String tipoNombre, int numero, String proyecto, LocalDateTime publicadoEn,
        List<ArchivoVista> archivos
    ) {
    }

    /** Copia no publicada lista para descargar (en memoria; no se guarda). */
    public record Copia(byte[] contenido, String mime, String nombre) {
    }

    /** Todos los tipos del proyecto con sus versiones: lo que el AUDITOR asignado puede hacer con cada uno. */
    @Transactional(readOnly = true)
    public ListaProyecto listar(TinyHatPrincipal principal, String proyectoPublicId) {
        exigirAuditor(principal);
        Auditoria proyecto = permisos.requireProyecto(principal, proyectoPublicId);
        long cuenta = principal.getCuentaId();
        Map<TipoInforme, Informe> series = new java.util.EnumMap<>(TipoInforme.class);
        informes.findByProyectoIdAndCuentaIdOrderByTipoAsc(proyecto.getIdAuditoria(), cuenta)
            .forEach(serie -> series.put(serie.getTipo(), serie));
        List<TipoEnLista> tipos = new ArrayList<>();
        for (TipoInforme tipo : TipoInforme.values()) {
            List<VersionResumen> resumenes = new ArrayList<>();
            Informe serie = series.get(tipo);
            if (serie != null) {
                for (InformeVersion v : versiones.findByInformeIdAndCuentaIdOrderByNumeroDesc(serie.getId(), cuenta)) {
                    resumenes.add(new VersionResumen(v.getPublicId(), v.getNumero(), v.getEstado().name(),
                        v.getPublicadoEn(), v.getDespublicadoEn(), v.getMotivoDespublicacion()));
                }
            }
            tipos.add(new TipoEnLista(tipo,
                resumenes.stream().anyMatch(r -> "BORRADOR".equals(r.estado())),
                resumenes.stream().anyMatch(r -> "PUBLICADO".equals(r.estado())), resumenes));
        }
        return new ListaProyecto(proyecto.getPublicId(), proyecto.getNombre(), tipos);
    }

    /** Editor de una versión: narrativa, formatos válidos, condiciones pendientes de publicar y archivos. */
    @Transactional(readOnly = true)
    public DetalleVersion detalle(TinyHatPrincipal principal, String versionPublicId) {
        exigirAuditor(principal);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        long cuenta = principal.getCuentaId();
        Informe serie = serieDe(version, cuenta);
        Auditoria proyecto = proyectoDe(serie, cuenta);
        Map<String, String> narrativa = leerNarrativa(version);
        List<SeccionEditable> secciones = serie.getTipo().secciones().stream()
            .map(s -> new SeccionEditable(s.clave(), s.etiqueta(), s.obligatoria(), narrativa.getOrDefault(s.clave(), "")))
            .toList();
        List<String> faltantes = List.of();
        if (version.getEstado() == EstadoVersion.BORRADOR) {
            try {
                InformeSnapshot vivo = ensamblador.ensamblar(proyecto, serie.getTipo(), version.getNumero(),
                    EstadoVersion.PUBLICADO, false, serie.getAnio(), narrativa, Instant.now(reloj));
                faltantes = validador.validar(vivo).faltantes();
            } catch (InformeValidacionException e) {
                faltantes = e.mensajes();
            }
        }
        List<ArchivoVista> lista = archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(version.getId(), cuenta).stream()
            .map(a -> new ArchivoVista(a.getPublicId(), a.getFormato().name(), a.getBytes(), a.getSha256())).toList();
        return new DetalleVersion(version.getPublicId(), proyecto.getPublicId(), proyecto.getNombre(), serie.getTipo(),
            version.getNumero(), version.getEstado(), idDe(serie, proyecto, version.getNumero()), secciones,
            List.copyOf(serie.getTipo().formatos().stream().sorted().toList()), faltantes, lista,
            version.getSnapshotSha256(), version.getPublicadoEn(), version.getDespublicadoEn(),
            version.getMotivoDespublicacion());
    }

    /** Informes PUBLICADOS de la organización del CLIENTE_ADMIN, los más recientes primero. */
    @Transactional(readOnly = true)
    public List<InformePublicadoVista> listarPublicadas(TinyHatPrincipal principal) {
        if (principal == null || !"CLIENTE_ADMIN".equals(principal.getRol()) || principal.getOrganizacionId() == null) {
            throw new ScopedNotFoundException();
        }
        long cuenta = principal.getCuentaId();
        List<InformePublicadoVista> lista = new ArrayList<>();
        for (InformeVersion v : versiones.findPublicadasDeOrganizacion(cuenta, principal.getOrganizacionId())) {
            Informe serie = serieDe(v, cuenta);
            Auditoria proyecto = proyectoDe(serie, cuenta);
            lista.add(new InformePublicadoVista(v.getPublicId(), idDe(serie, proyecto, v.getNumero()),
                serie.getTipo().nombre(), v.getNumero(), proyecto.getNombre(), v.getPublicadoEn(),
                archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(v.getId(), cuenta).stream()
                    .map(a -> new ArchivoVista(a.getPublicId(), a.getFormato().name(), a.getBytes(), a.getSha256()))
                    .toList()));
        }
        return lista;
    }

    /** Vista previa en HTML con la marca de no publicado (mismas plantillas que el PDF). */
    @Transactional(readOnly = true)
    public String previsualizarHtml(TinyHatPrincipal principal, String versionPublicId) {
        exigirAuditor(principal);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        Informe serie = serieDe(version, principal.getCuentaId());
        cupo(principal);
        boolean desdeBorrador = version.getEstado() == EstadoVersion.BORRADOR;
        String html = concurrencia.ejecutar(() -> {
            InformeSnapshot snapshot = desdeBorrador
                ? ensamblador.ensamblar(proyectoDe(serie, principal.getCuentaId()), serie.getTipo(), version.getNumero(),
                    EstadoVersion.BORRADOR, true, serie.getAnio(), leerNarrativa(version), Instant.now(reloj))
                : comoNoPublicado(SerializadorSnapshot.leer(version.getSnapshotJson()));
            return vistaPrevia.html(snapshot);
        });
        auditoria.ok(AuditEventType.INFORME_PREVISUALIZADO, principal, versionPublicId, Map.of("formato", "HTML"));
        return html;
    }

    /** Genera una copia NO PUBLICADA en memoria y devuelve su contenido con el nombre {@code {ID}-NP.ext}. */
    @Transactional(readOnly = true)
    public Copia generarCopia(TinyHatPrincipal principal, String versionPublicId, FormatoInforme formato) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        generarNoPublicado(principal, versionPublicId, formato, salida);
        InformeVersion version = permisos.requireInformeVersion(principal, versionPublicId);
        Informe serie = serieDe(version, principal.getCuentaId());
        String nombre = GeneradorIdInforme.generar(serie.getAnio(),
            proyectoDe(serie, principal.getCuentaId()).getEmpresa().getPublicId(),
            proyectoDe(serie, principal.getCuentaId()).getPublicId(), serie.getTipo(), version.getNumero(), true)
            + "." + formato.extension();
        return new Copia(salida.toByteArray(), formato.mime(), nombre);
    }

    // ================================================================ utilidades

    private static void exigirAuditor(TinyHatPrincipal principal) {
        if (principal == null || !ROL_AUDITOR.equals(principal.getRol())) {
            throw new ScopedNotFoundException();
        }
    }

    /** Rate limit por usuario de las operaciones costosas (clase {@code informe-generacion}, Bucket4j). */
    private void cupo(TinyHatPrincipal principal) {
        long espera = limitador.tryConsume("informe-generacion:" + principal.getCuentaId() + ":" + principal.getUserId(),
            seguridad.getRatelimit().getInformeGeneracion());
        if (espera > 0) {
            auditoria.registrar(AuditEventType.RATE_LIMITED, principal, "DENEGADO", null,
                Map.of("category", "informe-generacion", "subject", String.valueOf(principal.getUserId())));
            throw new RateLimitExceededException(espera);
        }
    }

    private Informe serieDe(InformeVersion version, long cuenta) {
        return informes.findByIdAndCuentaId(version.getInformeId(), cuenta).orElseThrow(ScopedNotFoundException::new);
    }

    private Auditoria proyectoDe(Informe serie, long cuenta) {
        return proyectos.findById(serie.getProyectoId()).filter(p -> p.getCuentaId() == cuenta)
            .orElseThrow(ScopedNotFoundException::new);
    }

    private static String idDe(Informe serie, Auditoria proyecto, int numero) {
        return GeneradorIdInforme.generar(serie.getAnio(), proyecto.getEmpresa().getPublicId(), proyecto.getPublicId(),
            serie.getTipo(), numero, false);
    }

    private Map<String, String> leerNarrativa(InformeVersion version) {
        String json = version.getNarrativaJson();
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(JSON.readValue(json, MAPA_TEXTOS));
    }

    private LocalDateTime ahoraUtc() {
        return LocalDateTime.now(reloj.withZone(ZoneOffset.UTC));
    }

    /** Claves en orden alfabético para que el JSON guardado sea estable. */
    private static final class TreeMapOrdenado extends java.util.TreeMap<String, String> {

        TreeMapOrdenado(Map<String, String> origen) {
            super(origen);
        }
    }

    /** Flujo de salida con tope de bytes: si el informe supera el máximo configurado se aborta. */
    private static final class SalidaLimitada extends FilterOutputStream {

        private final long maximo;
        private long escritos;

        SalidaLimitada(OutputStream salida, long maximo) {
            super(salida);
            this.maximo = maximo;
        }

        @Override
        public void write(int b) throws IOException {
            contar(1);
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            contar(len);
            out.write(b, off, len);
        }

        private void contar(int n) throws IOException {
            escritos += n;
            if (escritos > maximo) {
                throw new AlmacenInformes.InformeTamanoExcedidoException(maximo);
            }
        }

        @Override
        public void close() throws IOException {
            flush(); // el dueño del flujo es quien llama
        }
    }
}
