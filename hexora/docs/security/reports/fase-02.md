# Informe de Fase 02 — Modelo de datos (Flyway V18–V23)

## 1. Resumen

Se incorporó el modelo multicuenta de TinyHat y sus entidades, repositorios y servicio de capacidades. Las migraciones se ejecutaron desde V17 hasta V23 sobre MySQL 8.0.46 dentro de un contenedor desechable. La base compartida no se consultó ni modificó.

## 2. Criterios de aceptación

| ID | Verificación | Resultado |
|---|---|---|
| F2-CA1 | Migración V17 → V23 con conteos antes/después | **PASA**: todos los conteos de las 13 tablas heredadas seleccionadas coinciden. Antes y después: `organizacion_cliente` (antes `empresa`) 7, `auditoria` 2, `area_auditable` 3, `vulnerabilidad` 28; cada una de las 9 tablas de red verificadas tiene 0 filas. |
| F2-CA2 | Arranque de Spring con `ddl-auto=validate` | **PASA**: el contexto de la aplicación inició y Hibernate validó el esquema V23. |
| F2-CA3 | Hijo con `cuenta_id` distinto de su padre | **PASA**: insertar un `network_scan` de otra cuenta que referencia un `network_host` de la cuenta inicial es rechazado por la clave foránea compuesta. Las relaciones entre entidades que tienen padre usan claves compuestas; las tablas raíz de cada ámbito referencian `cuenta` directamente. |
| F2-CA4 | UUID públicos tras backfill | **PASA**: consultas de nulidad y duplicados sobre las 16 tablas con `public_id` no devuelven filas. |
| F2-CA5 | Capacidades de la cuenta inicial | **PASA**: `CapabilityService.isEnabled` devuelve `true` para `modulo_red`, `informes_pdf` y `monitoreo_continuo`. |

La prueba reproducible es [PhaseTwoMigrationIntegrationTest.java](../../../src/test/java/com/hexora/hexora/security/PhaseTwoMigrationIntegrationTest.java); el comando ejecutado fue `.\mvnw.cmd -Dtest=PhaseTwoMigrationIntegrationTest test` desde `hexora`. Resultado: **3 pruebas, 0 fallos, 0 errores**.

## 3. Migraciones y modelo añadido

- **V18** crea `cuenta`, evoluciona `empresa` a `organizacion_cliente`, asigna los registros existentes a la cuenta inicial y añade claves foráneas e índices de ámbito. Las relaciones padre-hijo aplicables usan claves compuestas con `cuenta_id`.
- **V19** crea `usuario` y `asignacion_auditor_proyecto`, con restricciones de rol, estado, email normalizado y organización.
- **V20** asigna UUID públicos únicos a los registros existentes de las tablas objetivo.
- **V21** crea las capacidades por cuenta y habilita las tres para la cuenta inicial.
- **V22** crea `informe` con estado y metadatos de publicación.
- **V23** crea `audit_event`, sus índices por fecha/cuenta/evento y referencias de ámbito.
- Se añadieron las entidades y repositorios correspondientes y `CapabilityService` como punto de consulta tipado.
- V18 también incorpora `network_scan`, `port_scan_result` y `network_discovery_snapshot_device`, además de las tablas de red mencionadas expresamente en el plan, para que las filas descendientes también se asocien a una cuenta.

## 4. Conteos y verificaciones

Los conteos se capturaron después de ejecutar hasta V17 y se compararon tras V23. La tabla `empresa` se compara con su nombre nuevo `organizacion_cliente`. El set de datos semilla heredado contiene 40 filas en las cuatro tablas core indicadas; no se encontraron filas en las tablas de red verificadas.

La prueba comprueba que la última versión instalada es V23, valida nulidad y unicidad de `public_id` en `cuenta`, organización, auditoría, áreas, vulnerabilidades, usuarios, informes y las tablas de red enumeradas en V20, y verifica las tres capacidades iniciales. La prueba de integridad usa una transacción que revierte sus datos de prueba.

## 5. Desviaciones y límites

1. **No existe una tabla previa de usuarios.** No hay usuarios auditores en V1–V17 que puedan backfillearse. V19 crea la tabla vacía.
2. **Compatibilidad heredada de `cuenta_id`.** Las tablas previas asignan datos a `cuenta_id=1` y usan ese valor por defecto para los flujos de escritura antiguos. Es una transición temporal, no una resolución de cuenta por petición. Debe retirarse o limitarse al introducir el aislamiento de las fases siguientes.
3. **Prueba aislada solamente.** No se aplicaron V18–V23 en la BD de desarrollo/compartida; el esquema probado es descartable.
4. **Sin consumo de capacidades en endpoints.** Según el alcance de esta fase, el servicio y el almacenamiento están listos, pero los endpoints aún no consultan capacidades.

## 6. STOP

Fase 2 lista para revisión. No avanzar a Fase 3 ni aplicar migraciones a una base compartida hasta recibir aprobación expresa.
