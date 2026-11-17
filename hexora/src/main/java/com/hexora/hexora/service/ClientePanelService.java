package com.hexora.hexora.service;

import com.hexora.hexora.informes.EstadoVersion;
import com.hexora.hexora.informes.snapshot.GeneradorIdInforme;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Informe;
import com.hexora.hexora.model.InformeVersion;
import com.hexora.hexora.repository.AsignacionClienteProyectoRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.InformeArchivoRepository;
import com.hexora.hexora.repository.InformeRepository;
import com.hexora.hexora.repository.InformeVersionRepository;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.TinyHatPrincipal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que ve el administrador de la MiPyme: sus proyectos activos y el historial de informes que el auditor publicó, con la
 * fecha de publicación y, si se retiró, la de despublicación. De un informe despublicado solo se ve la fila con sus
 * fechas: sus archivos ya no se descargan ni se verifican.
 */
@Service
public class ClientePanelService {

    public record ArchivoCliente(String publicId, String formato, long bytes) {
    }

    public record ProyectoCliente(
        String nombre, String estado, LocalDate inicio, LocalDate fin, String auditor, int informesVigentes
    ) {
    }

    public record EntradaInforme(
        String versionPublicId, String id, String tipo, int numero, String proyecto, LocalDateTime publicadoEn,
        LocalDateTime despublicadoEn, boolean vigente, List<ArchivoCliente> archivos
    ) {
    }

    public record Panel(String organizacion, List<ProyectoCliente> proyectos, List<EntradaInforme> informes, int vigentes) {
    }

    private final AsignacionClienteProyectoRepository asignaciones;
    private final InformeVersionRepository versiones;
    private final InformeRepository informes;
    private final InformeArchivoRepository archivos;
    private final AuditoriaRepository proyectos;
    private final CuentaRepository cuentas;

    public ClientePanelService(
        AsignacionClienteProyectoRepository asignaciones,
        InformeVersionRepository versiones,
        InformeRepository informes,
        InformeArchivoRepository archivos,
        AuditoriaRepository proyectos,
        CuentaRepository cuentas
    ) {
        this.asignaciones = asignaciones;
        this.versiones = versiones;
        this.informes = informes;
        this.archivos = archivos;
        this.proyectos = proyectos;
        this.cuentas = cuentas;
    }

    @Transactional(readOnly = true)
    public Panel panel(TinyHatPrincipal cliente) {
        if (cliente == null || !"CLIENTE_ADMIN".equals(cliente.getRol())) {
            throw new ScopedNotFoundException();
        }
        long cuenta = cliente.getCuentaId();
        String auditor = cuentas.findById(cuenta).map(Cuenta::getNombre).orElse("");
        List<EntradaInforme> historial = historial(cliente);
        List<ProyectoCliente> lista = new ArrayList<>();
        String organizacion = "";
        for (Auditoria p : asignaciones.proyectosActivos(cliente.getUserId(), cuenta)) {
            organizacion = p.getEmpresa().getNombre();
            int vigentes = (int) historial.stream().filter(e -> e.vigente() && e.proyecto().equals(p.getNombre())).count();
            lista.add(new ProyectoCliente(p.getNombre(), p.getEstado(), p.getFechaInico(), p.getFechaFin(), auditor, vigentes));
        }
        return new Panel(organizacion, lista, historial, (int) historial.stream().filter(EntradaInforme::vigente).count());
    }

    /** Informes de los proyectos a los que tiene acceso, los más recientes primero. */
    @Transactional(readOnly = true)
    public List<EntradaInforme> historial(TinyHatPrincipal cliente) {
        if (cliente == null || !"CLIENTE_ADMIN".equals(cliente.getRol())) {
            throw new ScopedNotFoundException();
        }
        long cuenta = cliente.getCuentaId();
        List<EntradaInforme> lista = new ArrayList<>();
        for (InformeVersion v : versiones.historialParaCliente(cuenta, cliente.getUserId())) {
            Informe serie = informes.findByIdAndCuentaId(v.getInformeId(), cuenta).orElse(null);
            Auditoria proyecto = serie == null ? null : proyectos.findById(serie.getProyectoId()).orElse(null);
            if (serie == null || proyecto == null) {
                continue;
            }
            boolean vigente = v.getEstado() == EstadoVersion.PUBLICADO;
            lista.add(new EntradaInforme(v.getPublicId(),
                GeneradorIdInforme.generar(serie.getAnio(), proyecto.getEmpresa().getPublicId(), proyecto.getPublicId(),
                    serie.getTipo(), v.getNumero(), false),
                serie.getTipo().nombre(), v.getNumero(), proyecto.getNombre(), v.getPublicadoEn(), v.getDespublicadoEn(),
                vigente,
                vigente ? archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(v.getId(), cuenta).stream()
                    .map(a -> new ArchivoCliente(a.getPublicId(), a.getFormato().name(), a.getBytes())).toList()
                    : List.of()));
        }
        return lista;
    }
}
