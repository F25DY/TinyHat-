# Registro de implementación de seguridad

## Fase 1 — Configuración segura, secretos y perfiles

- Se conservan Spring Boot 4.0.6, Spring Framework 7 y Hibernate 7 del proyecto; no se fuerza una actualización a las versiones del plan, que asume Boot 3/Security 6/Hibernate 6. Las APIs y pruebas deben ajustarse a las versiones instaladas.
- Las credenciales de base de datos, la clave de NVD, el correo de MyMemory y la clave MFA se leen de variables de entorno o del `.env` local no versionado. No se registran secretos.
- La carpeta raíz `.gitignore` del repositorio contiene artefactos previamente versionados y no es un archivo de reglas. Por ello, las reglas de exclusión se añadieron a `hexora/.gitignore`, junto al proyecto Maven, sin alterar ni eliminar esos artefactos preexistentes.
- La migración V2 contiene datos ficticios. Por autorización expresa, permanece intacta. La siembra heredada vive dentro de Flyway y no puede controlarse con `@Profile` ni con `tinyhat.demo.seed.enabled`; `baseline-on-migrate` solo baselina esquemas no vacíos, por lo que no evita que V2 se ejecute en una base realmente nueva. Esta desviación debe resolverse antes de desplegar producción con una base vacía, sin reescribir V2. No se aplicaron migraciones en esta fase.

## Fase 2 — Modelo de datos (V18–V23)

- Se añadieron las migraciones V18–V23 para cuenta/organización, usuarios/asignaciones, UUID públicos, capacidades, informes y eventos de auditoría.
- Se probó la secuencia completa desde V17 con datos semilla en un contenedor MySQL 8.0.46 desechable; no se conectó Flyway a la base compartida.
- El contexto de Spring arrancó con `spring.jpa.hibernate.ddl-auto=validate`. La prueba aislada verificó conteos, UUID, capacidades y rechazo de referencias entre cuentas; véase [fase-02.md](reports/fase-02.md).
- No había tabla de usuarios en V1–V17, por lo que V19 crea `usuario` vacía y no tiene filas de auditor existentes que migrar.
- Para mantener compatibilidad con los flujos heredados antes de introducir el contexto de cuenta, las tablas previas asignan sus filas a `cuenta_id=1` y mantienen ese valor como default. Esto no sustituye el aislamiento por cuenta de las fases posteriores.
- V18 incluye `network_scan`, `port_scan_result` y `network_discovery_snapshot_device` además de las tablas de red enumeradas en el plan, para que las tablas hijas también tengan ámbito de cuenta.
- Al reanudar el trabajo se encontraron ausentes del árbol de esta rama las cuatro clases fuente de seguridad que `HexoraApplication` importa. Se recuperaron sin modificaciones desde el commit existente `e77879b` de la misma línea de trabajo para restaurar la compilación.

La Fase 2 queda detenida para revisión y aprobación; no iniciar la Fase 3 ni aplicar estas migraciones a una base compartida antes de recibir autorización.
