# Informe de Fase 01 — Configuración segura, secretos y perfiles

## 1. Resumen

Se añadieron la configuración base segura, los perfiles `dev`, `demo` y `prod`, propiedades TinyHat validadas, una guarda de arranque, una plantilla de entorno y documentación para HTTPS local. No se editaron ni aplicaron migraciones. El trabajo queda detenido al cierre de esta fase.

## 2. Criterios de aceptación

| ID | Verificación | Resultado |
|---|---|---|
| F1-CA1 | Búsqueda `rg` de valores literales para credenciales de BD, MFA y claves NVD en `application*.properties` | **PASA**: sin coincidencias. `gitleaks` no está instalado; no se hizo análisis del historial Git. |
| F1-CA2 | Arranque del contexto de desarrollo con MySQL; prueba de credenciales obligatorias ausentes | **PASA**: la aplicación inició con el perfil predeterminado `dev` y la base conectada. La prueba de la guarda rechaza `DB_USER`/`DB_PASSWORD` vacíos con un mensaje explícito. |
| F1-CA3 | Contexto `prod` probado individualmente con cada ajuste prohibido | **PASA**: HTTPS, MFA, clave MFA, CSP, rangos públicos, sembrado demo, `ConsoleNotifier` y cookie `Secure` se prueban como rechazos de arranque. |
| F1-CA4 | Prueba MockMvc de `/error` con excepción y mensaje interno simulado | **PASA**: la respuesta no incluye mensaje ni traza. |
| F1-CA5 | Prueba local HTTPS con certificado PKCS12 temporal y confianza explícita del certificado | **PASA**: `curl --cacert ... https://localhost:18443/` recibió HTTP 200. Se eliminaron el certificado y el almacén temporales. |

## 3. Validación

`.\mvnw -q test`: **78 pruebas, 0 fallos, 0 errores, 0 omitidas**.

Durante el arranque de pruebas, Flyway validó los scripts disponibles y reportó la versión actual del esquema **16**, sin migraciones pendientes ni aplicadas. Se conservó la secuencia de migraciones.

## 4. Cambios realizados

- Se externalizaron credenciales y claves mediante variables de entorno; `.env.example` contiene valores vacíos o ejemplos comentados.
- Se añadieron `application-dev.properties`, `application-demo.properties` y `application-prod.properties`, con errores HTTP no verbosos, sesiones solo por cookie y configuración TLS local/prod.
- Se agregó validación de propiedades TinyHat y validación de credenciales DB/MFA para evitar arranque con requisitos ausentes.
- Se documentó la generación y prueba de certificados de desarrollo en [https-local.md](../https-local.md).
- Se añadieron las exclusiones a `hexora/.gitignore`; la ruta raíz `.gitignore` es actualmente un directorio con artefactos versionados, por lo que no se pudo usar como archivo.

## 5. Desviaciones y riesgos abiertos

1. El proyecto conserva Spring Boot 4.0.6, Spring 7 y Hibernate 7; el plan supone versiones anteriores. La adaptación se registra en [IMPLEMENTATION_LOG.md](../IMPLEMENTATION_LOG.md).
2. La siembra ficticia reside en Flyway V2 y no puede desactivarse mediante `@Profile` ni por `tinyhat.demo.seed.enabled`. V2 no se modificó por autorización expresa. Una base realmente nueva puede ejecutar V2; resolver esta limitación es requisito antes de desplegar producción sobre una base vacía.
3. `gitleaks` no está instalado. Se inspeccionaron con `rg` las propiedades de configuración para detectar asignaciones literales de credenciales y no hubo coincidencias; no se auditó el historial Git.
4. No se inició la Fase 2. El historial de la base sigue en V16 y la secuencia V17/V18 debe acordarse antes de diseñar o aplicar migraciones posteriores.

## 6. STOP

Fase 1 entregada para revisión. No avanzar a Fase 2 ni aplicar migraciones hasta recibir la aprobación de esta fase y resolver el orden de V17/V18.
