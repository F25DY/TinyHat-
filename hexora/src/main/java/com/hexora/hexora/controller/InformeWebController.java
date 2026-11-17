package com.hexora.hexora.controller;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.servicio.ErroresInforme;
import com.hexora.hexora.informes.servicio.InformeService;
import com.hexora.hexora.informes.servicio.InformeService.Copia;
import com.hexora.hexora.informes.servicio.InformeService.Descarga;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import com.hexora.hexora.security.TinyHatPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Vistas de los informes (§9). El AUDITOR asignado gestiona borradores, narrativa, vista previa, copias no publicadas,
 * publicación y despublicación; el CLIENTE_ADMIN solo ve y descarga lo publicado de su organización. Toda decisión de
 * acceso la toma {@link InformeService} (un recurso ajeno o no visible es un 404); aquí solo se validan las entradas y
 * se arman las respuestas. Todos los identificadores son UUID públicos y todos los POST llevan CSRF.
 */
@Controller
@Validated
public class InformeWebController {

    private static final String UUID_RE = "^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$";

    private final InformeService servicio;

    public InformeWebController(InformeService servicio) {
        this.servicio = servicio;
    }

    // ---------------------------------------------------------------- AUDITOR: lista y borradores

    @GetMapping("/proyectos/{proyectoUuid}/informes")
    @PreAuthorize("hasRole('AUDITOR')")
    public String lista(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String proyectoUuid,
        Model model
    ) {
        model.addAttribute("lista", servicio.listar(principal, proyectoUuid));
        return "informes/lista";
    }

    @PostMapping("/proyectos/{proyectoUuid}/informes/{tipo}/borradores")
    @PreAuthorize("hasRole('AUDITOR')")
    public String crearBorrador(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String proyectoUuid,
        @PathVariable TipoInforme tipo,
        RedirectAttributes aviso
    ) {
        try {
            return "redirect:/informes/versiones/" + servicio.crearBorrador(principal, proyectoUuid, tipo).getPublicId();
        } catch (ErroresInforme.Conflicto e) {
            aviso.addFlashAttribute("error", e.getMessage());
            return "redirect:/proyectos/" + proyectoUuid + "/informes";
        }
    }

    // ---------------------------------------------------------------- AUDITOR: editor y acciones

    @GetMapping("/informes/versiones/{versionUuid}")
    @PreAuthorize("hasRole('AUDITOR')")
    public String editor(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid,
        Model model
    ) {
        model.addAttribute("v", servicio.detalle(principal, versionUuid));
        return "informes/editor";
    }

    @PostMapping("/informes/versiones/{versionUuid}/narrativa")
    @PreAuthorize("hasRole('AUDITOR')")
    public String guardarNarrativa(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid,
        @RequestParam @NotBlank @Size(max = 64) String seccion,
        @RequestParam(defaultValue = "") @Size(max = 20000) String texto,
        RedirectAttributes aviso
    ) {
        try {
            servicio.guardarNarrativa(principal, versionUuid, seccion, texto);
            aviso.addFlashAttribute("mensaje", "Sección guardada.");
        } catch (ErroresInforme.Conflicto | InformeValidacionException e) {
            aviso.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/informes/versiones/" + versionUuid;
    }

    @GetMapping("/informes/versiones/{versionUuid}/vista-previa")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<String> vistaPrevia(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid
    ) {
        return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(servicio.previsualizarHtml(principal, versionUuid));
    }

    @PostMapping("/informes/versiones/{versionUuid}/generar")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<byte[]> generar(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid,
        @RequestParam FormatoInforme formato
    ) {
        Copia copia = servicio.generarCopia(principal, versionUuid, formato);
        return descarga(copia.contenido(), copia.mime(), copia.nombre());
    }

    @PostMapping("/informes/versiones/{versionUuid}/publicar")
    @PreAuthorize("hasRole('AUDITOR')")
    public String publicar(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid,
        @RequestParam(name = "formatos", required = false) List<FormatoInforme> formatos,
        RedirectAttributes aviso
    ) {
        Set<FormatoInforme> elegidos = formatos == null || formatos.isEmpty()
            ? EnumSet.noneOf(FormatoInforme.class) : EnumSet.copyOf(formatos);
        try {
            servicio.publicar(principal, versionUuid, elegidos);
            aviso.addFlashAttribute("mensaje", "Informe publicado.");
        } catch (InformeValidacionException e) {
            aviso.addFlashAttribute("errores", e.mensajes());
        } catch (ErroresInforme.Conflicto e) {
            aviso.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            aviso.addFlashAttribute("error", "Elija al menos un formato válido para este informe.");
        }
        return "redirect:/informes/versiones/" + versionUuid;
    }

    @PostMapping("/informes/versiones/{versionUuid}/despublicar")
    @PreAuthorize("hasRole('AUDITOR')")
    public String despublicar(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid,
        @RequestParam @NotBlank @Size(min = 10, max = 500) String motivo,
        RedirectAttributes aviso
    ) {
        try {
            servicio.despublicar(principal, versionUuid, motivo);
            aviso.addFlashAttribute("mensaje", "Informe despublicado. Puede crear una nueva versión.");
        } catch (ErroresInforme.Conflicto e) {
            aviso.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/informes/versiones/" + versionUuid;
    }

    @PostMapping("/informes/versiones/{versionUuid}/eliminar")
    @PreAuthorize("hasRole('AUDITOR')")
    public String eliminar(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid,
        RedirectAttributes aviso
    ) {
        String proyecto = servicio.detalle(principal, versionUuid).proyectoPublicId();
        try {
            servicio.eliminarBorrador(principal, versionUuid);
            aviso.addFlashAttribute("mensaje", "Borrador eliminado.");
            return "redirect:/proyectos/" + proyecto + "/informes";
        } catch (ErroresInforme.Conflicto e) {
            aviso.addFlashAttribute("error", e.getMessage());
            return "redirect:/informes/versiones/" + versionUuid;
        }
    }

    // ---------------------------------------------------------------- AUDITOR y CLIENTE_ADMIN

    @GetMapping("/informes/archivos/{archivoUuid}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public ResponseEntity<byte[]> descargar(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String archivoUuid
    ) {
        Descarga d = servicio.descargar(principal, archivoUuid);
        return descarga(d.contenido(), d.mime(), d.nombre());
    }

    @GetMapping("/informes/versiones/{versionUuid}/verificacion")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public String verificacion(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        @PathVariable @Pattern(regexp = UUID_RE) String versionUuid,
        Model model
    ) {
        model.addAttribute("verificacion", servicio.verificar(principal, versionUuid));
        model.addAttribute("esCliente", "CLIENTE_ADMIN".equals(principal.getRol()));
        return "informes/verificacion";
    }

    // ---------------------------------------------------------------- CLIENTE_ADMIN

    @GetMapping("/cliente/informes")
    @PreAuthorize("hasRole('CLIENTE_ADMIN')")
    public String informesDelCliente(@AuthenticationPrincipal TinyHatPrincipal principal, Model model) {
        model.addAttribute("informes", servicio.listarPublicadas(principal));
        return "informes/cliente";
    }

    // ---------------------------------------------------------------- respuestas de descarga

    /** Tipo exacto, adjunto, sin sniffing y sin caché (§8.3). */
    private static ResponseEntity<byte[]> descarga(byte[] contenido, String mime, String nombre) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(mime))
            .contentLength(contenido.length)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .body(contenido);
    }
}
