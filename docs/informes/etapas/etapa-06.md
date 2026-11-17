# Etapa 06 — Ciclo de vida, almacenamiento, auditoría y notificación

El servicio de informes (§8) sin controladores: crear borrador, narrativa, vista previa, copias NO PUBLICADAS, publicación atómica con archivos en disco, despublicación, verificación y descarga con comprobación de hash. Sin red y sin LLM (R4). El módulo de red no se tocó (R5).

## Hecho
- **`InformeService`** (paquete `informes.servicio`, sin dependencia del controlador, §8.4):
  - `crearBorrador` (I1: un solo borrador por serie; I6: precarga la narrativa de la última versión; los datos siempre se leen vivos), `guardarNarrativa` (sección válida del tipo, longitud máxima, solo en borrador), `eliminarBorrador` (solo borradores, I3/I5).
  - `previsualizar` (PDF NP) y `generarNoPublicado` (cualquier combinación válida): de los **datos vivos** si es borrador o del **snapshot** si la versión ya está publicada (única forma de obtener un formato que no se eligió al publicar). Nada se guarda.
  - `publicar` (§8.2, **atómico**): autoriza → exige borrador, formatos válidos y que no haya otra versión PUBLICADA (I2: "Despublique la versión vigente") → congela el snapshot con los datos vivos y su SHA-256 → valida **sobre el snapshot** → renderiza cada formato a un **temporal** calculando el SHA-256 mientras escribe → en la transacción mueve los archivos con `ATOMIC_MOVE`, registra los `informe_archivo` y publica la versión → **después del commit** audita y notifica. Si algo falla se borran temporales y archivos ya movidos (también si la transacción se revierte), el borrador queda intacto y se registra `INFORME_PUBLICACION_FALLIDA` en una transacción propia.
  - `despublicar` (motivo de 10 a 500 caracteres; los archivos se conservan como evidencia), `verificar` (solo muestra los hashes) y `descargar` (**recalcula el SHA-256 sobre los mismos bytes que entrega**; si no coincide o falta el archivo: error genérico, no se sirve nada y se registra `INFORME_INTEGRIDAD_FALLIDA`).
- **`AlmacenInformes` (§8.3):** `tinyhat.informes.dir`; ruta `{org}/{informe}/v{n}/{archivo}.{ext}` con tramos que solo pueden ser UUID; la BD guarda solo la ruta relativa; al resolver se normaliza, se exige el formato y que quede dentro del directorio base (también frente a enlaces simbólicos); permisos 700/600 donde el sistema los admite; tope de tamaño por archivo (`max-archivo-bytes`, 25 MiB) que **aborta** la escritura.
- **Concurrencia (§8.4):** `ControlConcurrenciaInformes` (semáforo con `tryAcquire` y espera corta) sobre generar, previsualizar y publicar; sin permiso, error `Ocupado` (503).
- **Rate limit:** clase `informe-generacion` de Bucket4j (5 por 60 s) **por usuario**, configurable en `tinyhat.security.ratelimit.informe-generacion.*`, auditada como `RATE_LIMITED` (D37).
- **Auditoría (§8.6):** `AuditoriaInformes` escribe los 10 eventos `INFORME_*` (más `INFORME_BORRADOR_ELIMINADO`) sobre `audit_event`, cada uno en su propia transacción, con correlationId, cuenta, usuario, organización y UUID de la versión; **nunca** narrativa ni hallazgos. Los `REPORT_*` del módulo anterior salen del catálogo y se cierra la excepción temporal D17 (D36).
- **Notificación (§8.5):** `NotificadorInformes` con `LogNotificadorInformes` por defecto y `SmtpNotificadorInformes` detrás de `tinyhat.informes.notificacion.habilitada=true` (CLIENTE_ADMIN activos de la organización; el mensaje solo dice que hay un informe nuevo y da el enlace de inicio de sesión). Se dispara con `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`; si falla, solo se registra. `Notifier` se amplió con `sendReportAvailableEmail` (D39).
- **Autorización:** solo un AUDITOR asignado opera; CLIENTE_ADMIN, AUDITOR sin asignación o de otra cuenta reciben el mismo `ScopedNotFoundException` (404). El CLIENTE_ADMIN solo verifica y descarga versiones PUBLICADAS de su organización.

## Archivos tocados
- Nuevos (main, `informes/servicio/`): `InformeService`, `AlmacenInformes`, `ControlConcurrenciaInformes`, `ErroresInforme`, `AuditoriaInformes`, `NotificadorInformes`, `LogNotificadorInformes`, `SmtpNotificadorInformes`, `EscuchaPublicacionInforme`.
- Modificados: `InformesProperties` (dir, tamaño máximo, concurrencia, notificación), `TinyHatSecurityProperties` (bucket `informe-generacion`), `application.properties`, `AuditEventType`, `Notifier`/`ConsoleNotifier`/`SmtpNotifier`, `InformeRepository` (`findByIdAndCuentaId`), `UsuarioRepository` (destinatarios), `InformeArchivo` (constructor con UUID), `PhaseTwelveAuditIntegrationTest` (se quita la excepción temporal), `.gitignore` (`hexora/var/`).
- Pruebas nuevas: `servicio/{AlmacenInformesTest, ControlConcurrenciaInformesTest, InformeServiceIntegrationTest}`.

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **774 pruebas, 0 fallos, 0 errores** (729 previas + 45 nuevas). La guardia `AuditAppendOnlyTest` obligó a que solo `AuditService` use el repositorio de auditoría: `AuditoriaInformes` delega en `AuditService.informeEvent`.
- **Almacén (26):** ruta con la forma del plan; 13 rutas hostiles rechazadas (`..`, absolutas, `C:/`, `\`, formato ajeno, extensión `.exe`, NUL, `tmp/`); hash y bytes mientras se escribe; aborto al superar el máximo; movimiento atómico sin sobrescribir; lectura acotada; permisos 700/600.
- **Concurrencia (3):** sin permiso tras la espera → `Ocupado`; el permiso se libera aunque el trabajo falle.
- **Integración con MySQL real y archivos en disco (16):**
  - **Ciclo completo:** borrador → narrativa → generar cada combinación válida (nada en disco) → vista previa → publicar PDF y DOCX (archivos con el hash del disco y de la base, sin temporales) → descargar y verificar como CLIENTE_ADMIN → despublicar (motivo obligatorio) → el cliente recibe 404 y el auditor sigue viendo → v2 con la narrativa precargada.
  - **Invariantes:** I1 (un borrador), I2 (una publicada; el historial del snapshot de la v2 lleva la v1 despublicada), I3/I5 (lo publicado no se edita ni se borra; transiciones válidas; el borrador se elimina físicamente), I4 (sin archivos de borradores), I6.
  - **Publicación fallida:** un renderizador que lanza error, y un fallo después de mover el primer archivo → versión sigue en BORRADOR, sin `informe_archivo`, **sin archivos ni temporales en disco**, evento `INFORME_PUBLICACION_FALLIDA` y sin notificación; el mensaje de error no filtra la causa.
  - **Integridad:** archivo alterado o borrado en disco → error genérico (sin ruta ni hash), no se sirve nada y queda `INFORME_INTEGRIDAD_FALLIDA` (2 eventos).
  - **Concurrencia:** con el máximo ocupado, generar y publicar responden `Ocupado`; liberado el permiso vuelve a funcionar.
  - **Rate limit:** la sexta operación seguida del mismo usuario recibe `RateLimitExceeded` con espera positiva y queda auditada; otro usuario no se ve afectado.
  - **Notificación:** se dispara tras el commit con solo identificadores; una falla del notificador no afecta la publicación.
  - **Autorización:** CLIENTE_ADMIN (propio y de otra organización), AUDITOR sin asignación y AUDITOR de otra cuenta reciben 404 en crear, editar, previsualizar, generar, publicar, despublicar, eliminar, verificar y descargar; el cliente propio sí verifica y descarga lo publicado.
  - **Auditoría sin contenido:** los detalles de todos los eventos `INFORME_*` no contienen la narrativa ni los títulos de los hallazgos.
  - **Copia NP desde snapshot:** un XLSX no publicado generado de una versión publicada lleva `-NP`, el aviso y el origen `SNAPSHOT`, y no se guarda.

## Criterios de aceptación
- ✔ Pruebas de integración de §11 (ciclo, invariantes, fallo, integridad, concurrencia) en verde.
- ✔ Cabeceras de descarga: se entregan el MIME exacto y el nombre `{ID}.{ext}` ya verificados; las cabeceras `Content-Disposition`, `nosniff` y `no-store` las pone el controlador (Etapa 7).

## Desviaciones y decisiones pendientes
- **D34** evento extra de eliminación; **D35** el PDF es obligatorio al publicar; **D36** `REPORT_*` sustituidos por `INFORME_*` (necesita tu visto bueno); **D37** rate limit por usuario desde el servicio; **D38** el hash se comprueba re-serializando (MySQL normaliza el JSON); **D39** `Notifier` ampliado y `@EnableAsync`; **D40** se permite crear la v n+1 con la anterior aún publicada.
- Siguen vigentes D20/D21, D24 (disparadores aplazados), D25, D30–D33.

## Riesgos o deuda detectada
- **Publicación síncrona con la conexión abierta:** el render ocurre dentro de la transacción (un TEC de 1000 hallazgos tarda ~8 s). Aceptable en la v1 con 2 generaciones a la vez; si crece, hay que sacar el render de la transacción.
- **Descarga en memoria:** el archivo (máx. 25 MiB) se lee entero para verificar y servir los mismos bytes; con muchas descargas simultáneas conviene limitar o pasar a flujo.
- **Respaldo:** `tinyhat.informes.dir` debe entrar en el respaldo junto con la BD (runbook, Etapa 8). Sin cifrado en reposo (§1.6, obligatorio antes de datos reales).
- **Auditoría independiente de la transacción:** cada evento tiene su transacción; un evento de éxito puede quedar sin la operación si esta se revierte después (p. ej. vista previa seguida de un fallo de la petición). Es deliberado: se prefiere dejar huella de más.
- **Eliminar la serie vacía:** al borrar el único borrador queda la serie (`informe`) sin versiones; no afecta nada.

ESTADO: EN ESPERA DE "APROBADA ETAPA 05" y "APROBADA ETAPA 06" (por indicación de la persona, aquí se detiene el trabajo)
