# Etapa 09 — Plantillas con más sustancia y vista del administrador de la MiPyme

Pedido de la persona tras aprobar las Etapas 7 y 8: (1) plantillas de informes más completas y mejor organizadas; (2) la vista del administrador de la MiPyme, con acceso por el auditor, contraseña temporal, cambio obligatorio, acceso solo con el proyecto activo y consulta de los informes con sus fechas.

## 1. Plantillas (versión de plantilla `2026.2`)
Todo sale del snapshot (nada inventado, sin IA); el PDF, el DOCX y el XLSX comparten las mismas cifras.
- **Lectura rápida** (indicadores): hallazgos, críticos y altos, riesgo más alto (NIST), abiertos y porcentaje de mitigados; con una **frase de postura** determinista (p. ej. «4 son de severidad Crítica o Alta y se sugiere atenderlos primero»).
- **Hoja de ruta por fases** (F1/F2/F3 sugeridas) con cuántos hallazgos hay en cada una, y **avance de la remediación** por estado (Abierta, En Progreso, Mitigada), con barras.
- **Dónde se concentra el riesgo:** una línea por área (hallazgos, críticos y altos, riesgo más alto).
- **Técnico:** nueva página «Lectura rápida» antes del índice y guía «Cómo leer este informe» (severidad frente a riesgo, fichas, anexos); el índice la incluye.
- **Ejecutivo y Final:** indicadores, hoja de ruta y avance en «Estado general» / «Estado del riesgo».
- **Matriz:** indicadores y resumen por área.
- **Mitigación:** el plan se organiza **por fases** (una tabla por fase con su descripción) en lugar de una sola tabla, precedido por la hoja de ruta.
- **Portada:** agrega el periodo del proyecto.
- **DOCX y XLSX:** incorporan lectura rápida, áreas, hoja de ruta y avance (XLSX: hoja Resumen).
- Pruebas actualizadas; se regeneraron las muestras de `docs/informes/muestras/`.

## 2. Vista del administrador de la MiPyme (CLIENTE_ADMIN)
**Flujo:** el auditor asignado abre el proyecto → «Acceso del cliente» → escribe el correo → el sistema crea la cuenta con una **contraseña temporal** (16 caracteres sin ambiguos, en cuatro grupos) que se muestra **una sola vez** para que se la entregue → la persona inicia sesión con ella → **el sistema solo le deja cambiarla** (cualquier otra ruta redirige a «Cambiar contraseña») → al guardar se cierran sus sesiones y entra con la nueva → ve su panel.
- **Seguridad:** la temporal solo existe en la respuesta al auditor (hash en la base, nunca en la auditoría ni en el registro); la política de contraseñas existente (12 caracteres, no comunes, distinta del correo) más «distinta de la actual» y confirmación; el cambio exige la contraseña actual; el límite de intentos de autenticación cubre `/cliente/contrasena`; CSRF en todo; el auditor puede **restablecer** (nueva temporal, cierra sesiones, obliga a cambiarla) y **retirar** el acceso (la fila queda como historia). Eventos de auditoría `CLIENTE_ACCESO_CONCEDIDO`, `_RESTABLECIDO` y `_RETIRADO` (sin contraseñas).
- **Acceso solo con el proyecto activo:** el CLIENTE_ADMIN puede iniciar sesión y mantener la sesión mientras tenga **al menos un proyecto asignado, con acceso vigente y distinto de «Completado»**. Si el proyecto se completa o se le retira el acceso, la sesión en curso se cierra en la siguiente petición y no puede volver a entrar; si el proyecto vuelve a estar activo, entra otra vez.
- **Panel (`/cliente`):** su organización, el proyecto o proyectos activos (estado, periodo, auditor, informes vigentes) y los informes entregados.
- **Informes (`/cliente/informes`):** historial, del más reciente al más antiguo, con la **fecha de publicación** y, si el auditor lo retiró, la **fecha de despublicación** («Retirado»). De un informe retirado solo se ve la fila: no hay descarga ni verificación (siguen siendo 404). De los vigentes: descarga por formato y verificación.
- **Alcance de los informes por proyecto asignado:** el cliente solo ve los informes de los proyectos a los que el auditor lo agregó (antes era por organización).
- **Menú:** el CLIENTE_ADMIN ve Panel, Informes y Contraseña (ya no los enlaces del auditor); el logo lo lleva a su panel; tras iniciar sesión va a su panel.

**Diseño (modo Operate, hereda el sistema existente):** se usan la página oscura, la barra lateral, las tarjetas, las tablas, los chips de estado y los diálogos del sitio, sin crear una identidad nueva; los estados llevan texto y no solo color; formularios con etiquetas visibles y ayuda enlazada; copiar la contraseña con un botón que aparece solo si el navegador lo permite; sin estilos ni scripts en línea (CSP). Archivos nuevos: `cliente.css`, `copiar-clave.js`, plantillas `cliente/{panel,contrasena}`, `acceso-cliente`, `fragments/informes-cliente`.

## Cambios técnicos
- **V34:** `usuario.debe_cambiar_password` y la tabla `asignacion_cliente_proyecto` (con `retirado_en`).
- **Nuevos:** `AccesoClienteProyecto` (regla de entrada), `PasswordChangeRequiredFilter`, `AccesoClienteService`, `ClientePanelService`, `AccesoClienteController`, `ClienteWebController`.
- **Modificados:** `TinyHatPrincipal`, `TinyHatUserDetailsService`, `SessionActivityFilter`, `SecurityConfiguration` (filtro, reglas `/cliente/**`, destino tras el login), `RateLimitCategory`, consultas de versiones y archivos de informes, `AuditEventType`/`AuditService`, `sidebar.html`, `detalleproyecto.html`, login.

## Pruebas
- `AccesoClienteIntegrationTest` (7): recorrido completo (agregar → temporal una sola vez y no en claro → login → redirección obligatoria → política y errores → cambio → la temporal deja de servir → panel → informe publicado con su fecha → despublicado como «Retirado» sin descargas); proyecto completado (sin acceso y sesión cerrada) y reactivado; retirar y volver a dar acceso; restablecer; aislamiento (auditor sin asignación o de otra cuenta → 404; el cliente no gestiona ni ve pantallas del auditor; sin sesión/CSRF); correos ajenos o inválidos; compatibilidad con cuentas CLIENTE_ADMIN anteriores sin asignaciones.
- Se actualizaron los fixtures de las pruebas de informes (los clientes ahora tienen asignación) y `SessionActivityFilterTest`.

## Desviaciones y decisiones pendientes
- **D44 (activo):** «proyecto activo» se define como distinto de «Completado» (incluye «Planificado» y «En Progreso»).
- **D45 (compatibilidad):** una cuenta CLIENTE_ADMIN **sin ninguna fila de asignación** (creada por la invitación por correo anterior) sigue entrando como antes, pero **no ve informes** hasta que el auditor la agregue a un proyecto. Al agregarla o retirarla, la regla estricta se aplica.
- **D46:** el cliente ve ahora las **fechas de despublicación** de los informes retirados (antes eran un 404 total); no ve motivo, ni archivos, ni verificación de esos.
- **D47:** la contraseña temporal se entrega **por el auditor** (como se pidió); no se envía por correo.
- **Pendiente tuyo:** revisión visual de las pantallas nuevas (no hay navegador en este entorno) y decidir si quieres que la contraseña temporal caduque (hoy no caduca hasta que se cambie o se restablezca).

ESTADO: EN ESPERA DE REVISIÓN
