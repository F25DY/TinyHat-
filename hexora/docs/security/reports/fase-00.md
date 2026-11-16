# Informe de Fase 00 — Inventario y línea base

## 1. Resumen

Se documentó el inventario de versiones, configuración, migraciones, endpoints, acceso a datos, entradas/salidas, procesos del sistema, autenticación y referencias a secretos en [00-inventario.md](../00-inventario.md).

No se implementó código de seguridad ni se modificó el esquema. Las ediciones de `pom.xml` y `application.properties` iniciadas antes de retomar el trabajo fueron deshechas por corresponder a una fase no aprobada. La rama de trabajo es `seguridad/fase-00`.

## 2. Migraciones

No se aplicaron migraciones. El repositorio contiene V1–V16, no V17. La consulta de solo lectura posterior confirmó que el contenedor MySQL está saludable y que `flyway_schema_history` termina en **V16 (success=1)**. La persona informa que V17 está en otra rama de mejoras del módulo de red y solicita reservar V18. Por lo tanto, el esquema local no coincide aún con el prerrequisito V17 del plan.

## 3. Criterios de aceptación

| ID | Cómo se verifica | Comando/prueba | Resultado |
|---|---|---|---|
| F0-CA1 | Inventario con todas las secciones requeridas y tabla de endpoints | Revisión de `docs/security/00-inventario.md` | PASA |
| F0-CA2 | `git status` limpio salvo `docs/security/**` | `git status --short` | PASA: solo aparecen los dos documentos de esta fase |
| F0-CA3 | Suite actual ejecutada y resultado registrado | `cd hexora; .\mvnw -q test` | PASA: línea base registrada; la ejecución reportó 63 pruebas, 0 fallos, 14 errores, 0 omitidas por `Communications link failure` al conectar con MySQL |

## 4. Línea base

`.\mvnw -q test` terminó con código 1. Resumen de Surefire: **63 ejecutadas / 0 failures / 14 errors / 0 skipped**. La causa observada fue `Unable to obtain connection from database: Communications link failure`. No se repitió `mvn -B verify` porque la base de datos necesaria no estaba disponible y la fase exige solo registrar la línea base.

Versiones resueltas: Spring Boot 4.0.6, Hibernate 7.2.12.Final, MySQL Connector/J 9.7.0, Flyway 11.14.1, Thymeleaf 3.1.5.RELEASE y Spring WebSocket 7.0.7. El POM requiere Java 25 y el runtime observado es Java 27.0.1. No existe Spring Security en el POM actual. El contenedor MySQL está declarado como `mysql:8.0`.

## 5. Desviaciones

No se realizó implementación; por ello no hay desviaciones funcionales que registrar en `IMPLEMENTATION_LOG.md`. El plan objetivo asume Boot 3/Security 6/Hibernate 6, mientras el proyecto usa Boot 4/Spring 7/Hibernate 7: al implementar habrá que adaptar APIs y registrar el motivo/alternativa conforme a R7.

## 6. Riesgos abiertos y bloqueos

1. **STOP por Flyway:** el plan exige V17 como última aplicada; el código solo contiene hasta V16 y la versión real en BD es desconocida. No iniciar Fase 1 hasta que la persona compruebe el historial y resuelva/documente la discrepancia.
2. **STOP por versión Flyway:** el esquema local termina en V16, mientras el plan requiere V17. La migración V17 de otra rama aún no está aplicada en esta base. Antes de fases con migraciones, debe integrarse/aplicarse V17 o aprobarse explícitamente una desviación documentada que evite colisiones.
3. La línea base inicial tuvo 14 errores de contexto porque MySQL no estaba disponible en ese momento. La reconexión posterior verifica el contenedor/BD, pero no se repitió la suite para evitar alterar datos fuera del inventario de solo lectura.
4. No hay autenticación real; `POST /login` concede redirección al dashboard sin verificar credenciales. No existe tabla de usuarios ni hash de contraseña.
5. No se comprobó el historial Git con gitleaks. Hay un `.env` local ignorado con configuración sensible de BD; sus valores no se leyeron ni se exponen aquí.

## 7. Comprobaciones manuales pendientes

1. La comprobación ya realizada encontró el contenedor `hexora-mysql` saludable y `127.0.0.1:3307` accesible. Para revalidar el historial de solo lectura, desde `hexora`:
   ```powershell
   docker exec hexora-mysql mysql --user=<usuario> --password --database=<base> --execute="SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
   ```
2. El resultado actual es `16  1`. Compararlo con la migración V17 de la otra rama antes de iniciar una fase que aplique migraciones.

**STOP:** no comenzar Fase 1 hasta que la persona revise este informe, resuelva los bloqueos y responda literalmente `APROBADA FASE 00`.
