# Registro de desviaciones — componente de informes

Rama: `feature/informes`. Documento rector: `TINYHAT_INFORMES_IMPLEMENTATION.md` (v1.0).
Regla R3: ninguna desviación se aplica sin aprobación de la persona responsable.

Estados: **pendiente**, **aprobada**, **rechazada**.

| ID | Fecha | Etapa | Decisión del documento | Qué se encontró en el código | Propuesta | Estado |
|---|---|---|---|---|---|---|
| D1 | 2026-11-16 | 0 → 2 | §3.2: crear las tablas `informe`, `informe_version` e `informe_archivo`. | La tabla `informe` **ya existe** (V22 + V28) con otra forma: un registro por informe, estados `BORRADOR/PUBLICADO`, tipos `TECNICO_PDF`, `EJECUTIVO_PDF`, `PLANES_MITIGACION_PDF`, `MATRIZ_RIESGO_XLSX`, snapshot en `contenido_json`. Tiene entidad `Informe`, `InformeRepository`, el paquete `com.hexora.hexora.report` (servicio, controlador REST `/api/...informes`, `ReportRenderer` sin implementaciones), y un panel en `detalleproyecto.html` con `project-reports.js`. Reutiliza `AuditEventType.REPORT_*` y la categoría de rate limit `PDF`. | Ver informe de Etapa 0, decisión **D1**. | pendiente |
| D2 | 2026-11-16 | 0 → 1 | §3.1 y §10: agregar `probabilidad TINYINT NULL` (1–5) a `vulnerabilidad`. | `vulnerabilidad.probabilidad` **ya existe**: `NOT NULL`, escala 0–10, con formulario, validación (`@Min(0) @Max(10)`) y uso en el cálculo de riesgo existente. | Ver **D2**. | pendiente |
| D3 | 2026-11-16 | 0 → 3 | §4.2: la severidad (5 niveles, con `INFORMATIVA`) sale de un campo propio o de la CIA. | No hay campo de severidad. Existe `nivelRiesgo` (4 niveles: Critico, Alto, Medio, Bajo) calculado con otra fórmula. No existe `INFORMATIVA`. | Ver **D3**. | pendiente |
| D4 | 2026-11-16 | 0 → 3 | §4.2: riesgo = probabilidad × impacto con tabla de búsqueda 5×5. | La app ya calcula `nivelRiesgo` como `((media CIA) + probabilidad) / 2` con umbrales 3/5/7 sobre escala 0–10. Los dos resultados pueden diferir para la misma vulnerabilidad. | Ver **D4**. | pendiente |
| D5 | 2026-11-16 | 0 → 3 | §4: el snapshot incluye descripción, evidencia, impacto textual, alcance y justificación del Anexo A. | Esos campos **no existen** (ver tabla de mapeo). Tampoco el estado "falso positivo". | Ver **D5**. | pendiente |
| D6 | 2026-11-16 | 0 → 3 | §4: `auditor.nombre`. | `Usuario` no tiene nombre de persona (solo correo). `Cuenta.nombre` sí existe. | Ver **D6**. | pendiente |
| D7 | 2026-11-16 | 0 → 6 | §8.4: clase de rate limit `INFORME_GENERACION` **por cuenta**. | El rate limit autenticado es **por usuario** (`UserRateLimitFilter`, clave `user:{id}:{categoría}`); `RateLimitCategory` es una enum con visibilidad de paquete y clasifica por ruta. | Ver **D7**. | pendiente |
| D8 | 2026-11-16 | 0 → 6 | §8.5: `SmtpNotificadorInformes` "usa el `SmtpNotifier` existente". | `SmtpNotifier` solo existe con perfil `prod`, implementa `Notifier` con 4 métodos fijos y su método `send` es privado. | Ver **D8**. | pendiente |
| D9 | 2026-11-16 | 0 | §2: POI "última 5.x estable". | Maven Central ya publica POI **6.0.0** (22-oct-2026). La última 5.x es **5.5.1**. | Usar 5.5.1 como indica el documento. Sin acción. | aprobada por el documento |
| D10 | 2026-11-16 | 0 → 5 | §7.3: `dispose()` en `finally`. | En POI 5.5.1 `SXSSFWorkbook.dispose()` está marcado como obsoleto. | Usar `close()` (que también elimina los temporales) en un `try-with-resources`. Mismo efecto, sin la advertencia. | pendiente |
| D11 | 2026-11-16 | 0 | §0/§2: "Java 25". | `pom.xml` fija `java.version=25`, pero el JDK instalado en la máquina de desarrollo es **27.0.1**. | Las pruebas de Etapa 0 corrieron en JDK 27. No requiere acción; se anota por si CI usa 25. | informativa |
